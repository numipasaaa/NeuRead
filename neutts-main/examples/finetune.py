import warnings
import re
import os
import json
import gc
import torch
import torchaudio
import torchaudio.transforms as T
import phonemizer
import neucodec
import numpy as np
from datasets import load_dataset, load_from_disk
from fire import Fire
from omegaconf import OmegaConf
from functools import partial
from transformers import (
    AutoTokenizer,
    AutoModelForCausalLM,
    Trainer,
    TrainingArguments,
    default_data_collator,
)
from loguru import logger as LOGGER
from torch import nn
from scipy.spatial.distance import euclidean
from scipy.ndimage import uniform_filter1d
from datasets import load_dataset


warnings.filterwarnings("ignore")

ACRONYM = re.compile(r"(?:[a-zA-Z]\.){2,}")
ACRONYM_NO_PERIOD = re.compile(r"(?:[A-Z]){2,}")



def extract_mfcc_features(waveform, sr=16000, n_mfcc=13, frame_length=400, hop_length=160):
    """Extract MFCC features from waveform for DTW alignment."""
    # Handle lists (from Arrow deserialization) and numpy arrays
    if isinstance(waveform, list):
        waveform = np.array(waveform)
    if isinstance(waveform, np.ndarray):
        waveform = torch.from_numpy(waveform).float()
    if waveform.dim() == 2:
        waveform = waveform.squeeze(0)
    
    mfcc_transform = T.MFCC(sample_rate=sr, n_mfcc=n_mfcc, melkwargs={"n_fft": 512, "hop_length": hop_length})
    mfcc = mfcc_transform(waveform)  # (n_mfcc, time_frames)
    return mfcc.T.cpu().numpy()  # (time_frames, n_mfcc)

def compute_dtw_distance(x, y):
    """Compute DTW cost matrix and return optimal path."""
    n, m = len(x), len(y)
    dtw_matrix = np.full((n + 1, m + 1), np.inf)
    dtw_matrix[0, 0] = 0
    
    for i in range(1, n + 1):
        for j in range(1, m + 1):
            cost = euclidean(x[i - 1], y[j - 1])
            dtw_matrix[i, j] = cost + min(dtw_matrix[i - 1, j], dtw_matrix[i, j - 1], dtw_matrix[i - 1, j - 1])
    
    # Backtrack to find alignment path
    i, j = n, m
    path = [(i - 1, j - 1)]
    while i > 1 or j > 1:
        candidates = []
        if i > 1 and j > 1:
            candidates.append((dtw_matrix[i - 1, j - 1], i - 1, j - 1))
        if i > 1:
            candidates.append((dtw_matrix[i - 1, j], i - 1, j))
        if j > 1:
            candidates.append((dtw_matrix[i, j - 1], i, j - 1))
        _, i, j = min(candidates)
        path.append((i - 1, j - 1))
    
    path.reverse()
    return path

def compute_phone_durations(waveform, sr, phones_list, codec=None):
    """
    Compute frame counts per phone using DTW-based forced alignment.
    Returns: list of frame counts (one per phone) normalized to code indices.
    """
    try:
        # Handle lists (from Arrow deserialization)
        if isinstance(waveform, list):
            waveform = np.array(waveform)
        if isinstance(waveform, np.ndarray):
            waveform_np = waveform
        elif isinstance(waveform, torch.Tensor):
            waveform_np = waveform.cpu().numpy()
        else:
            waveform_np = waveform
        
        # Extract MFCC for alignment
        mfcc = extract_mfcc_features(waveform_np, sr=sr)  # (time_frames, n_mfcc)

        n_phones = len(phones_list)
        if n_phones == 0 or len(mfcc) == 0:
            return [1] * n_phones if n_phones > 0 else [1]
        
        if codec is not None and isinstance(waveform, torch.Tensor):
            with torch.no_grad():
                codes = codec.encode_code(waveform.unsqueeze(0).unsqueeze(0) if waveform.dim() == 1 else waveform.unsqueeze(0))
                n_codes = len(codes.squeeze().cpu().tolist() if hasattr(codes.squeeze(), 'tolist') else codes.squeeze())
        else:
            # Estimate frame-to-code mapping: NeuCodec uses ~50ms hop length
            n_codes = max(1, int(len(waveform_np) / (sr * 0.050)))  # rough estimate

        def phone_to_feature_vector(phone, dim):
            vector = np.zeros(dim, dtype=np.float32)
            if not phone:
                return vector

            for index, char in enumerate(phone):
                vector[index % dim] += (ord(char) % 31 + 1) / 31.0

            norm = np.linalg.norm(vector)
            if norm > 0:
                vector /= norm
            return vector

        # Build one DTW template vector per phone and align MFCC frames to them.
        phone_features = np.stack(
            [phone_to_feature_vector(phone, mfcc.shape[1]) for phone in phones_list],
            axis=0,
        )
        dtw_path = compute_dtw_distance(mfcc.astype(np.float32), phone_features)

        frames_per_phone = [0] * n_phones
        for _, phone_idx in dtw_path:
            if 0 <= phone_idx < n_phones:
                frames_per_phone[phone_idx] += 1

        total_assigned = sum(frames_per_phone)
        if total_assigned == 0:
            frames_per_phone = [len(mfcc) // n_phones] * n_phones
            remainder = len(mfcc) % n_phones
            for i in range(remainder):
                frames_per_phone[i] += 1
            total_assigned = sum(frames_per_phone)

        # Map DTW-aligned frames to code counts while preserving the overall token budget.
        codes_per_phone = [max(1, int(round(n_codes * frame_count / total_assigned))) for frame_count in frames_per_phone]

        code_delta = n_codes - sum(codes_per_phone)
        if code_delta != 0:
            codes_per_phone[-1] = max(1, codes_per_phone[-1] + code_delta)
        
        return codes_per_phone
    except Exception as e:
        LOGGER.warning(f"Failed to compute phone durations: {e}. Returning uniform distribution.")
        n_phones = len(phones_list)
        # Return placeholder: distribute codes uniformly
        return [1] * n_phones if n_phones > 0 else [1]



def data_filter(sample):
    text = sample.get("text", sample.get("sentence", ""))

    if len(text) == 0:
        return False
    if re.search(r"\d", text):
        return False
    if re.search(ACRONYM, text) or re.search(ACRONYM_NO_PERIOD, text):
        return False
    if text[-1] not in ".,?!":
        return False
    return True

def encode_audio_to_codes(sample, codec, device):
    """Encode raw audio into NeuCodec VQ tokens."""
    audio_data = sample["audio"]
    waveform = torch.tensor(audio_data["array"], dtype=torch.float32)
    sample_rate = audio_data["sampling_rate"]

    # NeuCodec strictly expects 16kHz input audio for the encoder
    if sample_rate != 16000:
        resampler = torchaudio.transforms.Resample(orig_freq=sample_rate, new_freq=16000)
        waveform = resampler(waveform)

    # Ensure shape is (Batch, Channels, Time) -> (1, 1, T_16)
    if waveform.dim() == 1:
        waveform = waveform.unsqueeze(0).unsqueeze(0)
    elif waveform.dim() == 2:
        waveform = waveform.unsqueeze(0)

    waveform = waveform.to(device)

    with torch.no_grad():
        # The correct method name is encode_code
        codes = codec.encode_code(waveform)
        # Squeeze down the batch/channel dims to get a flat list of tokens
        codes_list = codes.squeeze().cpu().tolist()

    sample["codes"] = codes_list
    sample["text"] = sample.get("text", sample.get("sentence", ""))
    sample["waveform"] = waveform.squeeze().cpu().numpy()  # Store waveform for duration computation
    sample["sample_rate"] = 16000

    return sample

def preprocess_sample(sample, tokenizer, max_len, g2p, enable_duration_supervision=False, codec=None):
    speech_gen_start = tokenizer.convert_tokens_to_ids("<|SPEECH_GENERATION_START|>")
    ignore_index = -100

    vq_codes = sample["codes"]
    text = sample["text"]

    phones = g2p.phonemize([text])

    if not phones or not phones[0]:
        LOGGER.warning(f"[WARNING] Empty phonemization output for sample: {sample.get('__key__', 'unknown')} text={text}")
        return None

    phones = phones[0].split()
    phones_str = " ".join(phones)

    codes_str = "".join([f"<|speech_{i}|>" for i in vq_codes])

    chat = f"user: Convert the text to speech:<|TEXT_PROMPT_START|>{phones_str}<|TEXT_PROMPT_END|>\nassistant:<|SPEECH_GENERATION_START|>{codes_str}<|SPEECH_GENERATION_END|>"

    ids = tokenizer.encode(chat)

    ids = ids[:max_len]

    input_ids = torch.tensor(ids, dtype=torch.long)
    labels = torch.full_like(input_ids, ignore_index)

    speech_gen_start_idx = (input_ids == speech_gen_start).nonzero(as_tuple=True)[0]
    if len(speech_gen_start_idx) > 0:
        speech_gen_start_idx = speech_gen_start_idx[0]
        labels[speech_gen_start_idx:] = input_ids[speech_gen_start_idx:]

    # Since there is no padding, the attention mask is purely 1s
    attention_mask = torch.ones_like(input_ids)

    result = {
        "input_ids": input_ids,
        "labels": labels,
        "attention_mask": attention_mask,
        "text": text,  # Store original text
        "phones": phones_str,  # Store phoneme string for PER computation
    }

    # Add duration supervision targets if enabled
    if enable_duration_supervision:
        try:
            waveform = sample.get("waveform")
            sr = sample.get("sample_rate", 16000)
            if waveform is not None:
                phone_durations = compute_phone_durations(waveform, sr, phones, codec=codec)
                # Normalize to reference code counts
                target_duration = torch.tensor(phone_durations, dtype=torch.float32)
                target_duration = torch.log(target_duration + 1e-6)  # Log-normalize to stabilize training
                result["phone_durations"] = target_duration
                result["num_phones"] = len(phones)  # Track number of phones for grouping hidden states
            else:
                LOGGER.debug("No waveform available for duration supervision.")
        except Exception as e:
            LOGGER.warning(f"Failed to compute duration targets: {e}")

    return result

def duration_aware_data_collator(features):
    """Pad optional duration targets while delegating standard fields to HF collator."""
    has_duration = all("phone_durations" in f for f in features)
    stripped = [{k: v for k, v in f.items() if k not in ("phone_durations", "num_phones")} for f in features]
    batch = default_data_collator(stripped)

    if has_duration:
        durations = []
        counts = []
        for f in features:
            d = f["phone_durations"]
            if isinstance(d, torch.Tensor):
                d = d.tolist()
            d = [float(x) for x in d]
            durations.append(d)
            counts.append(int(f.get("num_phones", len(d))))

        max_len = max(len(d) for d in durations) if durations else 0
        padded = [d + [0.0] * (max_len - len(d)) for d in durations]

        batch["phone_durations"] = torch.tensor(padded, dtype=torch.float32)
        batch["num_phones"] = torch.tensor(counts, dtype=torch.long)

    return batch

class DurationSupervisionTrainer(Trainer):
    """Custom Trainer supporting multi-task learning: causal LM + duration supervision."""
    def __init__(self, *args, enable_duration_supervision=False, duration_loss_weight=0.1, 
                  **kwargs):
        super().__init__(*args, **kwargs)
        self.enable_duration_supervision = enable_duration_supervision
        self.duration_loss_weight = duration_loss_weight

        # Duration head is attached in main() so checkpoint loading can restore it.
        if self.enable_duration_supervision:
            if not hasattr(self.model, "duration_head"):
                hidden_size = self.model.config.hidden_size if hasattr(self.model, 'config') else 768
                model_dtype = next(self.model.parameters()).dtype
                self.model.duration_head = nn.Linear(hidden_size, 1).to(device=self.model.device, dtype=model_dtype)
            self.mse_loss = nn.MSELoss(reduction='mean')
            LOGGER.info(f"Duration supervision enabled with loss weight: {duration_loss_weight}.")
    
    def compute_loss(self, model, inputs, return_outputs=False, num_items_in_batch=None):
        """Compute combined loss: causal LM + duration MSE loss."""
        labels = inputs.get("labels", None)

        phone_durations = inputs.pop("phone_durations", None)
        num_phones = inputs.pop("num_phones", None)

        outputs = model(**inputs)
        
        if self.args.past_index >= 0:
            self._past = outputs[self.args.past_index]
        
        # Use model-native LM loss to avoid extra memory spikes from manual CE flattening.
        lm_loss = outputs.loss if hasattr(outputs, 'loss') and outputs.loss is not None else torch.tensor(0.0, device=outputs.logits.device)
        duration_loss = torch.tensor(0.0, device=lm_loss.device)
        
        total_loss = lm_loss
        
        # Add duration supervision loss if enabled
        if self.enable_duration_supervision and phone_durations is not None and num_phones is not None:
            try:
                # Low-memory path: use token embeddings instead of full hidden-states from all layers.
                input_ids = inputs.get("input_ids", None)
                attention_mask = inputs.get("attention_mask", None)
                if input_ids is None:
                    duration_loss = torch.tensor(0.0, device=lm_loss.device)
                    total_loss = lm_loss
                else:
                    input_embeds = model.get_input_embeddings()(input_ids)
                    batch_size = input_embeds.shape[0]
                    duration_predictions = []
                    duration_targets = []

                    for i in range(batch_size):
                        if isinstance(phone_durations, (list, tuple)):
                            sample_durations = torch.tensor(phone_durations[i], dtype=torch.float32, device=lm_loss.device)
                        elif phone_durations.dim() > 1:
                            sample_durations = phone_durations[i].to(lm_loss.device)
                        else:
                            sample_durations = phone_durations.to(lm_loss.device)

                        if labels is not None and labels.dim() > 1:
                            speech_positions = (labels[i] != -100).nonzero(as_tuple=True)[0]
                        elif attention_mask is not None:
                            speech_positions = (attention_mask[i] > 0).nonzero(as_tuple=True)[0]
                        else:
                            speech_positions = torch.arange(input_embeds.shape[1], device=input_embeds.device)

                        if speech_positions.numel() == 0 or sample_durations.numel() == 0:
                            continue

                        speech_embeds = input_embeds[i, speech_positions, :]
                        token_idx = 0
                        for duration_target in sample_durations:
                            # Invert log-normalization: log(x + 1e-6) -> exp(y) - 1e-6 to recover span length
                            duration_target_int = max(1, int(round(np.exp(float(duration_target.item())) - 1e-6)))
                            token_end = min(token_idx + duration_target_int, speech_embeds.shape[0])
                            if token_end <= token_idx:
                                break

                            # Mean-pool tokens aligned to one phone, then predict that phone duration.
                            phone_embed = speech_embeds[token_idx:token_end].mean(dim=0, keepdim=True)
                            duration_head = model.module.duration_head if hasattr(model, "module") else model.duration_head
                            phone_embed = phone_embed.to(dtype=duration_head.weight.dtype)
                            duration_pred = duration_head(phone_embed).squeeze(0).squeeze(-1)
                            duration_predictions.append(duration_pred)
                            duration_targets.append(duration_target)
                            token_idx = token_end

                    if len(duration_predictions) > 0:
                        duration_predictions = torch.stack(duration_predictions)
                        duration_targets = torch.stack(duration_targets).to(duration_predictions.dtype)
                        duration_loss = self.mse_loss(duration_predictions, duration_targets)
                        total_loss = lm_loss + self.duration_loss_weight * duration_loss
                    else:
                        duration_loss = torch.tensor(0.0, device=lm_loss.device)
                        total_loss = lm_loss
            except Exception as e:
                LOGGER.warning(f"Failed to compute duration loss: {e}. Using LM loss only.")
                total_loss = lm_loss
                duration_loss = torch.tensor(0.0, device=lm_loss.device)
        
        if hasattr(self, "log") and self.state is not None:
            log_every = max(1, int(getattr(self.args, "logging_steps", 1)))
            if self.state.global_step % log_every == 0:
                if self.enable_duration_supervision:
                    self.log({
                        "lm_loss": float(lm_loss.detach().item()),
                        "duration_loss": float(duration_loss.detach().item()),
                        "total_loss": float(total_loss.detach().item()),
                    })
                else:
                    self.log({"lm_loss": float(lm_loss.detach().item())})

        return (total_loss, outputs) if return_outputs else total_loss
def _try_load_duration_head_weights(model, checkpoint_dir):
    """Load duration_head weights from a checkpoint directory if present."""
    duration_state = {}
    safetensors_path = os.path.join(checkpoint_dir, "model.safetensors")
    index_path = os.path.join(checkpoint_dir, "model.safetensors.index.json")
    bin_path = os.path.join(checkpoint_dir, "pytorch_model.bin")

    try:
        if os.path.exists(safetensors_path):
            from safetensors.torch import load_file as load_safetensors_file
            state = load_safetensors_file(safetensors_path)
            duration_state = {k.replace("duration_head.", ""): v for k, v in state.items() if k.startswith("duration_head.")}
        elif os.path.exists(index_path):
            with open(index_path, "r", encoding="utf-8") as f:
                index_data = json.load(f)
            weight_map = index_data.get("weight_map", {})
            shard_files = sorted({fname for key, fname in weight_map.items() if key.startswith("duration_head.")})
            if shard_files:
                from safetensors.torch import load_file as load_safetensors_file
                for shard in shard_files:
                    shard_path = os.path.join(checkpoint_dir, shard)
                    if os.path.exists(shard_path):
                        state = load_safetensors_file(shard_path)
                        for k, v in state.items():
                            if k.startswith("duration_head."):
                                duration_state[k.replace("duration_head.", "")] = v
        elif os.path.exists(bin_path):
            state = torch.load(bin_path, map_location="cpu")
            duration_state = {k.replace("duration_head.", ""): v for k, v in state.items() if k.startswith("duration_head.")}
    except Exception as e:
        LOGGER.warning(f"Failed to inspect checkpoint for duration_head weights: {e}")
        duration_state = {}

    if duration_state:
        missing, unexpected = model.duration_head.load_state_dict(duration_state, strict=False)
        if missing:
            LOGGER.warning(f"duration_head missing keys when loading checkpoint: {missing}")
        if unexpected:
            LOGGER.warning(f"duration_head unexpected keys when loading checkpoint: {unexpected}")
        LOGGER.info("Loaded duration_head weights from checkpoint.")
    else:
        LOGGER.warning("No duration_head weights found in checkpoint; using freshly initialized head.")

def main(config_fpath: str):
    print(f"Loading config from {config_fpath}")
    config = OmegaConf.load(config_fpath)
    checkpoints_dir = os.path.join(config.save_root, config.run_name)
    LOGGER.info(f"Logging to: {checkpoints_dir}")

    # Duration supervision config
    enable_duration_supervision = config.get("enable_duration_supervision", False)
    duration_loss_weight = config.get("duration_loss_weight", 0.1)

    
    if enable_duration_supervision:
        LOGGER.info(f"Duration supervision enabled with loss weight: {duration_loss_weight}")


    restore_from = config.restore_from

    use_bf16 = torch.cuda.is_available() and torch.cuda.is_bf16_supported()
    use_fp16 = torch.cuda.is_available() and not use_bf16
    model_dtype = torch.bfloat16 if use_bf16 else (torch.float16 if use_fp16 else torch.float32)

    print(f"Loading checkpoint from {restore_from}")
    tokenizer = AutoTokenizer.from_pretrained(restore_from)
    model = AutoModelForCausalLM.from_pretrained(restore_from, dtype=model_dtype)

    # ensure lm_head exists and is tied to input embeddings if checkpoint omitted it
    if getattr(model.lm_head, "weight", None) is None:
        hidden_size = getattr(model.config, "hidden_size", 768)
        vocab_size = getattr(model.config, "vocab_size", None) or tokenizer.vocab_size
        dtype = next(model.parameters()).dtype
        device = model.device
        model.lm_head = nn.Linear(hidden_size, vocab_size, bias=False).to(device=device, dtype=dtype)
        # tie weights so lm_head uses same weight matrix as token embeddings
        model.lm_head.weight = model.get_input_embeddings().weight
        LOGGER.warning("lm_head.weight was missing in checkpoint — created lm_head and tied weights to input embeddings.")
    else:
        LOGGER.info(f"lm_head.weight found in checkpoint and will be used for training.")

    if enable_duration_supervision:
        hidden_size = model.config.hidden_size if hasattr(model, "config") else 768
        if not hasattr(model, "duration_head"):
            model.duration_head = nn.Linear(hidden_size, 1).to(device=model.device, dtype=next(model.parameters()).dtype)
        _try_load_duration_head_weights(model, restore_from)
    
    model.config.use_cache = False
    LOGGER.info(f"Model dtype: {model_dtype}. Training precision -> bf16={use_bf16}, fp16={use_fp16}")

    g2p = phonemizer.backend.EspeakBackend(
        language="ro",
        preserve_punctuation=True,
        with_stress=True,
        words_mismatch="ignore",
        language_switch="remove-flags",
    )

    # Define paths for dataset checkpoints
    # Encoded dataset: after audio encoding (compact - codes only, no raw audio)
    encoded_dataset_path = os.path.join(config.save_root, "encoded_romanian_dataset")
    # Processed dataset: after preprocessing (ready for training)
    processed_dataset_path = os.path.join(config.save_root, "processed_romanian_dataset")
    
    # Keep codec off GPU by default to avoid stealing VRAM from the LM.
    device = "cuda" if torch.cuda.is_available() else "cpu"
    codec_device = config.get("codec_device", "cpu")
    codec = None

    partial_preprocess = partial(
        preprocess_sample,
        tokenizer=tokenizer,
        max_len=config.max_seq_len,
        g2p=g2p,
        enable_duration_supervision=enable_duration_supervision,
        codec=codec,
    )

    if os.path.exists(processed_dataset_path):
        print(f"Loading preprocessed dataset from {processed_dataset_path}...")
        processed_dataset = load_from_disk(processed_dataset_path)
        if enable_duration_supervision:
            missing_duration_cols = (
                "phone_durations" not in processed_dataset.column_names
                or "num_phones" not in processed_dataset.column_names
            )
            if missing_duration_cols:
                LOGGER.warning(
                    "Processed dataset is missing duration columns; rebuilding from encoded dataset for duration supervision."
                )
                if os.path.exists(encoded_dataset_path):
                    encoded_dataset = load_from_disk(encoded_dataset_path)
                    processed_dataset = encoded_dataset.map(
                        partial_preprocess,
                        remove_columns=["codes"],
                        desc="Rebuilding processed dataset with duration targets"
                    )
                    processed_dataset.save_to_disk(processed_dataset_path)
                else:
                    raise RuntimeError(
                        "Duration supervision is enabled, but existing processed dataset has no duration targets and encoded dataset was not found. "
                        f"Please regenerate encoded dataset at {encoded_dataset_path} or disable duration supervision."
                    )
    else:
        # Check if we have the encoded dataset already
        if os.path.exists(encoded_dataset_path):
            print(f"Loading encoded dataset from {encoded_dataset_path}...")
            encoded_dataset = load_from_disk(encoded_dataset_path)
        else:
            print("Downloading, filtering, and encoding dataset...")
            romanian_dataset = load_dataset(
                "eduardem/romanian-speech-v2",
                split="train[:214278]",
            )

            if codec is None:
                print(f"Loading NeuCodec on {codec_device}...")
                codec = neucodec.NeuCodec.from_pretrained("neuphonic/neucodec").to(codec_device)
                codec.eval()
            
            encode_audio_partial = partial(encode_audio_to_codes, codec=codec, device=codec_device)

            # Filter and encode (expensive step)
            print("Filtering dataset...")
            encoded_dataset = romanian_dataset.filter(data_filter)
            
            print("Encoding audio to speech codes (this takes a while)...")
            encoded_dataset = encoded_dataset.map(
                encode_audio_partial,
                desc="Encoding audio to NeuCodec VQ tokens"
            )
            
            # Remove raw audio to save space (keep only codes, text, waveform for duration)
            print("Removing raw audio to save disk space...")
            encoded_dataset = encoded_dataset.remove_columns(["audio"])

            print(f"Saving encoded dataset to {encoded_dataset_path}...")
            print(f"(Compact format: codes only, no raw audio)")
            encoded_dataset.save_to_disk(encoded_dataset_path)
            print(f"Encoded dataset saved!")

        # Preprocess the encoded dataset (cheap step, can iterate)
        print("Preprocessing encoded dataset...")
        processed_dataset = encoded_dataset.map(
            partial_preprocess,
            remove_columns=["codes"],
            desc="Preprocessing (tokenizing and formatting)"
        )

        print(f"Saving preprocessed dataset to {processed_dataset_path}...")
        processed_dataset.save_to_disk(processed_dataset_path)
        print("Preprocessed dataset saved successfully!")

    # Ensure preprocessing codec does not remain in memory during LM training.
    if codec is not None:
        del codec
        gc.collect()

    torch.cuda.empty_cache()

    training_args = TrainingArguments(
        output_dir=checkpoints_dir,
        do_train=True,
        learning_rate=config.lr,
        max_steps=config.max_steps,
        bf16=use_bf16,
        fp16=use_fp16,
        per_device_train_batch_size=config.per_device_train_batch_size,
        gradient_accumulation_steps=config.get("gradient_accumulation_steps", 4),
        gradient_checkpointing=True,
        optim="paged_adamw_8bit",
        warmup_ratio=config.warmup_ratio,
        save_steps=config.save_steps,
        logging_steps=config.logging_steps,
        save_strategy="steps",
        ignore_data_skip=True,
        dataloader_drop_last=True,
        remove_unused_columns=False,
        torch_compile=False,
        dataloader_num_workers=4,
    )

    trainer = DurationSupervisionTrainer(
        model=model,
        tokenizer=tokenizer,
        args=training_args,
        train_dataset=processed_dataset,
        data_collator=duration_aware_data_collator if enable_duration_supervision else default_data_collator,
        enable_duration_supervision=enable_duration_supervision,
        duration_loss_weight=duration_loss_weight,
    )
    trainer.train(resume_from_checkpoint=True)
    trainer.save_model(checkpoints_dir)

if __name__ == "__main__":
    Fire(main)
