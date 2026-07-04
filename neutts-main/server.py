import io
import os
import tempfile
import torch
import soundfile as sf
import wave
import gc
import numpy as np
import warnings
import whisper
from jiwer import wer
import librosa
from contextlib import asynccontextmanager
from fastapi import FastAPI, HTTPException, UploadFile, File, Response
from pydantic import BaseModel
from neutts import NeuTTS
from typing import List, Union, Optional, Dict

# Global Whisper model
asr_model = None
WER_THRESHOLD = 0.30
MAX_RETRIES = 1
WER_TRACKING = True

# Suppress inference on CPU warnings
warnings.filterwarnings('ignore', message='Performing inference on CPU when CUDA is available')


@asynccontextmanager
async def lifespan(app: FastAPI):
    global asr_model
    print(f"--- SERVER STARTUP (Device: {device}) ---")
    print(f"Checking Romanian Finetune Path: {ROMANIAN_FINETUNE_PATH}")
    if os.path.isdir(ROMANIAN_FINETUNE_PATH):
        print("OK: Romanian finetune path exists.")
    else:
        print("WARNING: Romanian finetune path NOT FOUND.")

    try:
        # Initial load of default model
        get_tts_instance(ROMANIAN_FINETUNE_PATH, "ro")
    except Exception as e:
        print(f"Startup error: {e}")

    if WER_TRACKING:
        try:
            print("[INIT] Loading Whisper large-v3 for WER testing (on CPU)...")
            asr_model = whisper.load_model("large-v3", device="cpu")
            print("[INIT] Whisper loaded successfully.")
        except Exception as e:
            print(f"Whisper load error: {e}")

    for name, info in INTEGRATED_VOICES.items():
        if info.get("ref_codes_path") and os.path.exists(info["ref_codes_path"]):
            print(f"Loaded ref codes for: {name}")
            info["ref_codes"] = torch.load(info["ref_codes_path"], map_location=device)
    print("--- SERVER READY ---")
    yield
    # Clean up
    tts_instances.clear()
    gc.collect()
    if torch.cuda.is_available():
        torch.cuda.empty_cache()

app = FastAPI(title="NeuTTS Complete Voice API", lifespan=lifespan)

# Global cache for NeuTTS instances - strictly ONE instance at a time to avoid OOM
tts_instances: Dict[tuple, NeuTTS] = {}
device = "cuda" if torch.cuda.is_available() else "cpu"

SAMPLE_RATE = 24000
SAMPLE_WIDTH = 2  # 16-bit

# Model Paths
ROMANIAN_FINETUNE_PATH = "./data/neutts-romanian-finetune/checkpoint-370000"
DEFAULT_MODEL = "neuphonic/neutts-air"

def normalize_lang(l: Optional[str]) -> str:
    """Ensures language codes are compatible with espeak backend."""
    if not l:
        return "en-us"
    l_clean = l.lower().replace("_", "-")
    if l_clean.startswith("ro"):
        return "ro"
    if l_clean.startswith("en"):
        return "en-us"
    if l_clean.startswith("fr"):
        return "fr-fr"
    if l_clean.startswith("es"):
        return "es"
    if l_clean.startswith("de"):
        return "de"
    return l_clean

def get_tts_instance(model_repo: str, language: Optional[str] = None):
    global tts_instances

    # Path normalization for local directories
    actual_model = model_repo
    if os.path.isdir(model_repo):
        actual_model = os.path.normpath(os.path.abspath(model_repo))
        if not actual_model.endswith(os.path.sep):
            actual_model += os.path.sep

    # Strictly normalize language for espeak
    norm_lang = normalize_lang(language)

    key = (actual_model, norm_lang)

    if key in tts_instances:
        return tts_instances[key]

    # Switch logic: Clear previous instances to free VRAM
    if tts_instances:
        print(f"\n[VRAM] >>> SWITCHING MODEL <<<")
        print(f"[VRAM] Purging active instances: {list(tts_instances.keys())}")
        tts_instances.clear()
        gc.collect()
        if torch.cuda.is_available():
            torch.cuda.empty_cache()
            try:
                free_mem, _ = torch.cuda.mem_get_info()
                print(f"[VRAM] GPU Memory Cleared. Free: {free_mem // 1024**2} MB")
            except Exception:
                print(f"[VRAM] GPU Memory Cleared.")

    print(f"[INIT] Loading NeuTTS Model: {actual_model} [Lang: {norm_lang}]")
    try:
        instance = NeuTTS(
            backbone_repo=actual_model,
            backbone_device=device,
            codec_repo="neuphonic/neucodec",
            codec_device=device,
            language=norm_lang
        )
        tts_instances[key] = instance
        return instance
    except Exception as e:
        print(f"[ERROR] CRITICAL: Failed to load model: {e}")
        import traceback
        traceback.print_exc()
        raise e

def calculate_wer(audio_segment_wav, reference_text, lang_code="en"):
    """Transcribes audio using Whisper and calculates WER."""
    global asr_model
    if asr_model is None:
        print("[WER] Whisper model not initialized, skipping WER check.")
        return 0.0

    # Whisper expects 16kHz audio
    if SAMPLE_RATE != 16000:
        audio_16k = librosa.resample(audio_segment_wav, orig_sr=SAMPLE_RATE, target_sr=16000)
    else:
        audio_16k = audio_segment_wav

    try:
        whisper_lang = lang_code
        if lang_code.lower() == "en-us":
            whisper_lang = "en"
        elif lang_code.lower() == "fr-fr":
            whisper_lang = "fr"

        # User requested specific transcribe call
        print(f"[WER] Ref: \"{reference_text}\"")
        asr_result = asr_model.transcribe(
            audio_16k,
            language=whisper_lang,
            task="transcribe",
            fp16=False
        )
        print(f"[WER] Hyp: \"{asr_result['text']}\"")
        error_rate = wer(reference_text.lower(), asr_result["text"].lower())

        print(f"[WER] Score: {error_rate:.4f}")


        return error_rate
    except Exception as e:
        print(f"[WER] Error calculating WER: {e}")
        return 0.0

# Integrated voices
INTEGRATED_VOICES = {
    "jo": {
        "ref_text": "So I just tried Neuphonic and I’m genuinely impressed. It's super responsive, it sounds clean, supports voice cloning, and the agent feature is fun to play with too. Highly recommend it for podcasts, conversations, or even just messing around with voiceovers.",
        "ref_codes_path": "samples/jo.pt",
        "language": "en-us",
        "model": DEFAULT_MODEL
    },
    "dave": {
        "ref_text": "So I'm live on radio. And I say, well, my dear friend James here clearly, and the whole room just froze. Turns out I'd completely misspoken and mentioned our other friend.",
        "ref_codes_path": "samples/dave.pt",
        "language": "en-us",
        "model": DEFAULT_MODEL
    },
    "adrian": {
        "ref_text": "Apoi Dumnezeu a zis, să dea pământul verdeață, iarbă cu sămânță, pomi roditori, care să facă rod după soiul lor și care să aibă în ei sămânța lor pe pământ.",
        "ref_codes_path": "samples/adrian.pt",
        "language": "ro",
        "model": ROMANIAN_FINETUNE_PATH
    },
    "andreea": {
        "ref_text": "Mai aprinse odată bricheta și se stinse următorul bec. Și tot așa, până când aleia se cufundă în beznă, singurele două luminițe fiind ochii motanului, care nu-și deslipea privirea de la noul venit.",
        "ref_codes_path": "samples/andreea.pt",
        "language": "ro",
        "model": ROMANIAN_FINETUNE_PATH
    },
    "mihaela": {
        "ref_text": "Maşinile, care erau de obicei strălucitoare, stăteau pline de praf în parcările lor şi peluzele, cândva verzi ca smaraldul, erau purjolite şi se îngălbeneau, pentru că folosirea furtunurilor fusese interzisă din cauza secetei.",
        "ref_codes_path": "samples/mihaela.pt",
        "language": "ro",
        "model": ROMANIAN_FINETUNE_PATH
    },
    "mihai": {
        "ref_text": "Apoi Dumnezeu a zis, să dea pământul verdeață, iarbă cu sămânță, pomi roditori, care să facă rod după soiul lor și care să aibă în ei sămânța lor pe pământ.",
        "ref_codes_path": "samples/mihai.pt",
        "language": "ro",
        "model": ROMANIAN_FINETUNE_PATH
    },
    "mateo": {
        "ref_text": "Además su eficiencia depende del clima. En días nublados o durante la noche producen menos energía.",
        "ref_codes_path": "samples/mateo.pt",
        "language": "es",
        "model": DEFAULT_MODEL
    },
    "greta": {
        "ref_text": "Es wurde eine Untersuchung zur Aufklärung des Unfalls eingeleitet.",
        "ref_codes_path": "samples/greta.pt",
        "language": "de",
        "model": DEFAULT_MODEL
    },
    "juliette": {
        "ref_text": "Dans les zones rurales où de nombreuses communautés n'ont pas accès à l'électricité, l'énergie solaire peut fare une énorme différence.",
        "ref_codes_path": "samples/juliette.pt",
        "language": "fr-fr",
        "model": DEFAULT_MODEL
    },
}

DEFAULT_VOICE = "jo"

class TTSRequest(BaseModel):
    sentences: List[str]
    pause_seconds: float = 0.15
    voice: Optional[str] = "jo"
    language: Optional[str] = None
    model: Optional[str] = None

@app.post("/tts")
async def tts_endpoint(data: TTSRequest):
    print(f"\n[REQUEST] TTS: voice={data.voice}, lang={data.language}, model={data.model}")

    v_query = data.voice.lower() if data.voice else DEFAULT_VOICE

    # Map incoming voice query to integrated voice names
    if any(name in v_query for name in ["adrian", "andreea", "mihaela", "mihai"]) or "romanian" in v_query:
        # For generic "romanian" query, pick first one or allow it to be handled by vi lookup if specific name exists
        if "adrian" in v_query: v_name = "adrian"
        elif "andreea" in v_query: v_name = "andreea"
        elif "mihaela" in v_query: v_name = "mihaela"
        elif "mihai" in v_query: v_name = "mihai"
        else: v_name = "adrian" # default romanian
    elif v_query.startswith("dave"):
        v_name = "dave"
    elif v_query.startswith("jo"):
        v_name = "jo"
    elif v_query.startswith("mateo"):
        v_name = "mateo"
    elif v_query.startswith("greta"):
        v_name = "greta"
    elif v_query.startswith("juliette"):
        v_name = "juliette"
    else:
        v_name = v_query

    vi = INTEGRATED_VOICES.get(v_name, INTEGRATED_VOICES[DEFAULT_VOICE])

    # Determine normalized language
    lang = normalize_lang(data.language or vi.get("language"))

    # Determine model repo
    if data.model:
        model_repo = data.model
    elif lang == "ro" or v_name in ["adrian", "andreea", "mihaela", "mihai"]:
        model_repo = ROMANIAN_FINETUNE_PATH
        lang = "ro"
    else:
        model_repo = vi.get("model", DEFAULT_MODEL)

    print(f"[DEBUG] Final Model: {model_repo}, Language: {lang}")

    try:
        tts = get_tts_instance(model_repo, lang)

        ref_codes = vi.get("ref_codes")
        if ref_codes is None:
            ref_codes = torch.zeros((1, 100), device=device)

        combined_audio = io.BytesIO()
        durations = []


        for sentence in data.sentences:
            if WER_TRACKING:
                retry_count = 0
                while retry_count <= MAX_RETRIES:
                    audio_segment_wav = tts.infer(
                        text=sentence,
                        ref_codes=ref_codes,
                        ref_text=vi.get("ref_text", "")
                    )

                    wer = calculate_wer(audio_segment_wav, sentence, lang_code=lang)
                    if wer <= WER_THRESHOLD:
                        break

                    retry_count += 1
                    if retry_count == 1:
                        copy_segment_wav = audio_segment_wav
                        wer_copy = wer
                        print(f"[WER] Threshold exceeded ({wer:.4f} > {WER_THRESHOLD}). Retrying synthesis...")
                        continue

                    print(f"[WER] Threshold exceeded ({wer:.4f} > {WER_THRESHOLD}). Choosing best version...")
                    if wer > wer_copy:
                        audio_segment_wav = copy_segment_wav
            else:
                audio_segment_wav = tts.infer(
                    text=sentence,
                    ref_codes=ref_codes,
                    ref_text=vi.get("ref_text", "")
                )

            duration_ms = (len(audio_segment_wav) / SAMPLE_RATE) * 1000
            durations.append(int(duration_ms + (data.pause_seconds * 1000)))
            combined_audio.write((audio_segment_wav * 32767).astype(np.int16).tobytes())
            combined_audio.write(b'\x00' * int(SAMPLE_RATE * SAMPLE_WIDTH * data.pause_seconds))

        wav_io = io.BytesIO()
        with wave.open(wav_io, "wb") as wav_file:
            wav_file.setnchannels(1)
            wav_file.setsampwidth(SAMPLE_WIDTH)
            wav_file.setframerate(SAMPLE_RATE)
            wav_file.writeframes(combined_audio.getvalue())

        return Response(content=wav_io.getvalue(), media_type="audio/wav", headers={
            "X-Sentence-Durations-Ms": ",".join(map(str, durations)),
            "Access-Control-Expose-Headers": "X-Sentence-Durations-Ms"
        })
    except Exception as e:
        print(f"[ERROR] TTS Processing failed: {e}")
        raise HTTPException(status_code=500, detail=str(e))

@app.post("/encode_reference")
def encode_reference_endpoint(language: Optional[str] = "ro", ref_audio: UploadFile = File(...)):
    try:
        with tempfile.NamedTemporaryFile(delete=False, suffix=".wav") as temp:
            temp.write(ref_audio.file.read())
            temp_path = temp.name
        # use default model for encoding
        lang = normalize_lang(language)
        if lang == "ro":
            tts = get_tts_instance(ROMANIAN_FINETUNE_PATH, lang)
        else:
            tts = get_tts_instance(DEFAULT_MODEL, lang)
        codes = tts.encode_reference(temp_path).cpu().tolist()
        os.remove(temp_path)
        return {"codes": codes}
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

class CloneRequest(BaseModel):
    sentences: List[str]
    ref_text: str
    ref_codes: Union[List[int], List[List[int]]]
    pause_seconds: float = 0.15
    language: Optional[str] = None
    model: Optional[str] = None

@app.post("/clone_with_codes")
async def clone_with_codes_endpoint(data: CloneRequest):
    lang = normalize_lang(data.language)
    if data.model:
        model_repo = data.model
    elif lang == "ro":
        model_repo = ROMANIAN_FINETUNE_PATH
    else:
        model_repo = DEFAULT_MODEL

    try:
        print(f"Language: {lang}")
        tts = get_tts_instance(model_repo, lang)
        ref_codes = torch.tensor(data.ref_codes, device=device)
        combined_audio = io.BytesIO()
        durations = []
        for sentence in data.sentences:
            if WER_TRACKING:
                retry_count = 0
                while retry_count <= MAX_RETRIES:
                    audio_segment_wav = tts.infer(text=sentence, ref_codes=ref_codes, ref_text=data.ref_text)

                    wer = calculate_wer(audio_segment_wav, sentence, lang_code=lang)
                    if wer <= WER_THRESHOLD:
                        break

                    retry_count += 1
                    if retry_count == 1:
                        copy_segment_wav = audio_segment_wav
                        wer_copy = wer
                        print(f"[WER] Threshold exceeded ({wer:.4f} > {WER_THRESHOLD}). Retrying synthesis...")
                        continue

                    print(f"[WER] Threshold exceeded ({wer:.4f} > {WER_THRESHOLD}). Choosing best version...")
                    if wer > wer_copy:
                        audio_segment_wav = copy_segment_wav
            else:
                audio_segment_wav = tts.infer(
                    text=sentence,
                    ref_codes=ref_codes,
                    ref_text=data.ref_text
                )

            duration_ms = (len(audio_segment_wav) / SAMPLE_RATE) * 1000
            durations.append(int(duration_ms + (data.pause_seconds * 1000)))
            combined_audio.write((audio_segment_wav * 32767).astype(np.int16).tobytes())
            combined_audio.write(b'\x00' * int(SAMPLE_RATE * SAMPLE_WIDTH * data.pause_seconds))

        wav_io = io.BytesIO()
        with wave.open(wav_io, "wb") as wav_file:
            wav_file.setnchannels(1)
            wav_file.setsampwidth(SAMPLE_WIDTH)
            wav_file.setframerate(SAMPLE_RATE)
            wav_file.writeframes(combined_audio.getvalue())

        return Response(content=wav_io.getvalue(), media_type="audio/wav", headers={
            "X-Sentence-Durations-Ms": ",".join(map(str, durations)),
            "Access-Control-Expose-Headers": "X-Sentence-Durations-Ms"
        })
    except Exception as e:
        print(f"[ERROR] Clone processing failed: {e}")
        raise HTTPException(status_code=500, detail=str(e))

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)
