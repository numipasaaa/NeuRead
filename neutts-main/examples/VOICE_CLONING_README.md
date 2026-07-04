# Ghid tehnic pentru clonarea vocală NeuTTS

## Rezumat

Acest document descrie implementarea actuală din `neutts-main` pentru clonarea vocală NeuTTS. În versiunea prezentă, clonarea nu este antrenată ca un pipeline separat de speaker embeddings; comportamentul activ este unul de **condiționare pe audio de referință**:

- modelul primește un fișier audio de referință (`.wav`)
- audio-ul este encodat în coduri NeuCodec
- la inferență, textul nou este generat în stilul acelei referințe
- antrenarea din `examples/finetune.py` se ocupă de codificare text/audio și de supervizarea duratei

Fluxul principal de inferență folosește `NeuTTS.encode_reference()` și `NeuTTS.infer(...)` cu `ref_codes` și `ref_text`.

# Ghid tehnic pentru clonarea vocală NeuTTS

## Rezumat

Acest document descrie implementarea actuală din `neutts-main` pentru clonarea vocală NeuTTS. În versiunea prezentă, clonarea este bazată pe **condiționare pe audio de referință**:

- modelul primește un fișier audio de referință (`.wav`)
- audio-ul este encodat în coduri NeuCodec
- la inferență, textul nou este generat în stilul acelei referințe
- antrenarea din `examples/finetune.py` se ocupă de codificare text/audio și de supervizarea duratei

## Cum funcționează implementarea actuală

### 1. Condiționarea pe audio de referință

Fluxul de clonare folosește:

1. un audio de referință (`.wav`)
2. un fișier `.txt` cu textul de referință asociat, dacă există
3. `NeuTTS.encode_reference()` pentru a extrage codurile de referință
4. `NeuTTS.infer(text, ref_codes, ref_text)` pentru generarea vocii

Aceasta este calea activă folosită de aplicație.

### 2. Coduri de vorbire

Implementarea activă se bazează pe codurile de referință și pe textul de referință pentru condiționarea generării.

### 3. Antrenarea din `examples/finetune.py`

Scriptul de fine-tuning actual:

- încarcă setul de date `eduardem/romanian-speech-v2`
- filtrează exemplele prin `data_filter()`
- encodează audio-ul în coduri NeuCodec prin `encode_audio_to_codes()`
- tokenizează textul și fonemele în `preprocess_sample()`
- calculează opțional supervizarea duratei prin `compute_phone_durations()` și `DurationSupervisionTrainer`
- folosește `duration_aware_data_collator()` atunci când supervizarea duratei este activată, pentru a completa țintele de durată și a grupa câmpurile custom ale batch-ului

## Comenzi și fișiere relevante

### Scriptul de fine-tuning

```bash
python finetune.py finetune_config.yaml
```

### Configurația activă

Fișierul `examples/finetune_config.yaml` conține în prezent setările de bază pentru antrenare și supervizarea duratei:

```yaml
# run info
restore_from: "./data/neutts-romanian-finetune/checkpoint-370000" 
save_root: "./data"
run_name: "neutts-romanian-finetune"

# model info
codebook_size: 65536
max_seq_len: 1024   # Further reduced for 8GB GPUs
codec_device: "cuda:0"
lr: 0.00004
lr_scheduler_type: "cosine"
warmup_ratio: 0.00

# train info
per_device_train_batch_size: 1
gradient_accumulation_steps: 4  # Simulates a larger batch size over multiple backward passes
max_steps: 500000
logging_steps: 500
save_steps: 10000
seed: 1337

# Duration supervision (optional - set to false to disable)
# ============================
enable_duration_supervision: true     # Set to true to enable duration loss during training
duration_loss_weight: 0.05             # λ: weight of duration loss (0.05-0.5 range)
```

## Detalii despre inferență

Fluxul actual de inferență:

- construiește obiectul `NeuTTS`
- încarcă textul de referință din fișierul `.txt` asociat, dacă există
- encodează referința cu `tts.encode_reference()` sau încarcă `.pt`-ul cache-uit
- sintetizează cu `tts.infer(args.text, ref_codes, ref_text)`

### Puncte importante tehnic

- output-ul NeuTTS este salvat la 24000 Hz în mod implicit
- dacă salvarea eșuează, există fallback la 16000 Hz

## Structura reală a datelor în antrenare

### După `encode_audio_to_codes()`

```python
{
    "codes": [1234, 5678, ...],
    "text": "Sample text",
    "waveform": np.array(...),
    "sample_rate": 16000
}
```

### După `preprocess_sample()`

```python
{
    "input_ids": tensor([...]),
    "labels": tensor([...]),
    "attention_mask": tensor([...]),
    "text": "Sample text",
    "phones": "s ə m p ə l",
    "phone_durations": tensor([...]),
    "num_phones": 7
}
```

## Pipeline-ul de antrenare, pe scurt

### Pașii principali

1. `data_filter()` elimină exemplele nepotrivite
2. `encode_audio_to_codes()` encodează audio-ul în coduri NeuCodec
3. `preprocess_sample()` construiește promptul chat și etichetele pentru LM
4. dacă este activată supervizarea duratei, `compute_phone_durations()` generează ținte pentru durate
5. `DurationSupervisionTrainer` antrenează modelul cu `TrainingArguments`

### Ce este important de reținut

- `remove_unused_columns=False` este setat pentru a păstra câmpurile custom ale datasetului
- `trainer.train(resume_from_checkpoint=True)` înseamnă că reluarea din checkpoint este implicită
- optimizerul folosește `optim="paged_adamw_8bit"`
- `duration_aware_data_collator()` este folosit ca data-aware collator în regimul cu supervizare a duratei; în rest se folosește `default_data_collator`

### Detalii despre `duration_aware_data_collator()`

`duration_aware_data_collator()` primește o listă de exemple deja preprocesate de `preprocess_sample()`. Fiecare exemplu are, în mod obișnuit:

- `input_ids`
- `labels`
- `attention_mask`
- `text`
- `phones`
- opțional `phone_durations`
- opțional `num_phones`

Comportamentul lui este următorul:

1. extrage separat câmpurile de durată (`phone_durations`, `num_phones`)
2. trimite restul batch-ului către `default_data_collator()` pentru câmpurile standard ale LM
3. dacă toate exemplele din batch au `phone_durations`, le reunește într-un tensor `torch.float32`
4. pad-uiește secvențele mai scurte cu `0.0` până la lungimea maximă din batch
5. returnează `phone_durations` cu forma `[batch_size, max_num_phones]`
6. returnează `num_phones` ca tensor `torch.long` cu forma `[batch_size]`

Interpretare practică:

- partea de limbaj rămâne gestionată de collator-ul standard Hugging Face
- partea de durată este tratată separat, ca să nu fie pierdută la colarea batch-ului
- padding-ul cu `0.0` marchează pozițiile inexistente din exemplele mai scurte

În regimul fără supervizare a duratei, acest collator nu este folosit; trainerul primește batch-uri standard prin `default_data_collator`.

### Cum este folosită informația de durată în trainer

`DurationSupervisionTrainer` consumă câmpurile `phone_durations` și `num_phones` doar când supervizarea duratei este activă. În acel caz:

- calculează loss-ul LM obișnuit
- reconstruiește segmentele pe foneme din reprezentările tokenilor
- compară predicțiile de durată cu țintele din batch
- adaugă un termen MSE ponderat cu `duration_loss_weight`

Dacă batch-ul nu conține durate sau supervizarea este dezactivată, trainingul rămâne exclusiv LM-only.

## Exemplu de utilizare corectă

```python
from neutts import NeuTTS

tts = NeuTTS(
    backbone_repo="./data/neutts-romanian-finetune/checkpoint-370000",
    backbone_device="cuda",
    codec_repo="neuphonic/neucodec",
    codec_device="cuda",
    language="ro",
)

ref_audio_path = "samples/mihai.wav"
ref_text = open("samples/mihai.txt", "r").read().strip()
ref_codes = tts.encode_reference(ref_audio_path)

wav = tts.infer("Bună ziua, acesta este un test de clonare vocală.", ref_codes, ref_text)
```


## Referințe utile

- `examples/finetune.py` — antrenarea și preprocesarea datelor
- `examples/finetune_config.yaml` — configurația activă
- `README.md` din rădăcina proiectului — ghidul principal și exemplele de referință

## Concluzie

Implementarea actuală NeuTTS din `neutts-main` este centrată pe **reference-audio cloning**: encodezi un audio de referință, păstrezi `ref_codes` și `ref_text`, apoi sintetizezi textul nou în stilul acelei referințe.