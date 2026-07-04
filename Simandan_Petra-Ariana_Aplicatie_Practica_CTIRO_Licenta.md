# NeuRead Android - Descrierea Livrabilelor Proiectului

**Autor:** Șimandan Petra-Ariana  
**Aplicație:** NeuRead - Aplicație Mobilă de Lectură Audio Bazată pe Modele Neurale
**Sesiunea:** Iulie 2026  
**Tip:** Proiect Licență

---

## 1. Adresa Repository-ului

### Repository-uri Publice

Codul sursă complet al proiectului este disponibil la următoarele adrese:

#### 1.1 Repository Principal (Aplicație Android și folder neutts-main)
```
Repository: https://github.com/numipasaaa/NeuRead
Vizibilitate: Public
Branch: master
```

#### 1.2 Checkpoint-ul modelului NeuTTS finetunat
```
Repository: https://huggingface.co/psimandan/neutts-romanian-finetune
Vizibilitate: Public
Branch: main
```

## 2. Pașii de Compilare ai Aplicației

### 2.1 Cerințe Preliminare

#### Pentru aplicația Android:
```
- Android Studio
- JDK 21
- Android SDK cu API Level 24+ (minimum)
- Gradle 
```

#### Pentru backend (NeuTTS Server):
```
- Python 3.13
- pip 
- NVIDIA GPU (recomandat)
- NVIDIA cuDNN (pentru accelerare GPU)
```

### 2.2 Compilare Aplicație Android

#### Pasul 1: Clonare repository-ului
```bash
git clone https://github.com/numipasaaa/NeuRead.git
cd NeuRead
```

#### Pasul 2: Configurare variabile locale
Creează fișierul `local.properties` în rădăcina proiectului:
```properties
sdk.dir=/path/to/android-sdk
```

Sau configurează prin Android Studio:
- File → Project Structure → SDK Location

#### Pasul 3: Build Debug APK
```bash
./gradlew assembleDebug
```
Output: `app/build/outputs/apk/debug/app-debug.apk`

#### Pasul 4: Build Release APK
```bash
./gradlew assembleRelease
```
Output: `app/build/outputs/apk/release/app-release.apk`


#### Alternative - Compilare prin Android Studio
1. Deschide File → Open și selectează directorul NeuRead
2. Lasă Gradle să sincronizeze automat (Build → Make Project)
3. Selectează Build Variant: Debug sau Release
4. Run → Debug 'app'

### 2.3 Compilare Backend (NeuTTS Server)

#### Pasul 1: Clonare și configurare
```bash
git clone https://github.com/numipasaaa/NeuRead.git # dacă nu a fost făcut deja acest pas
cd NeuRead/neutts-main
```

#### Pasul 2: Creare environment virtual Python
```bash
python3 -m venv venv
source venv/bin/activate  # Linux
```

#### Pasul 3: Instalare dependențe
```bash
pip install -r requirements.txt
```

#### Pasul 4: Descarcă checkpoint-ul finetunat și plasează-l în neutts-main/data/neutts-romanian-finetune
```bash
Link: https://huggingface.co/psimandan/neutts-romanian-finetune
```

#### Pasul 5: Build Docker (Opțional)

##### Pentru CPU:
```bash
docker build -f neutts-main/Dockerfile -t neutts-server:latest .
```

##### Pentru GPU (CUDA):
```bash
docker build -f neutts-main/Dockerfile.gpu -t neutts-server:gpu .
```


---

## 3. Pașii de Instalare și Lansare a Aplicației

### 3.1 Instalare pe Dispozitiv Android

#### Metoda 1: Prin Android Studio
1. Conectează dispozitivul Android prin USB
2. Activează Developer Mode: Settings → About Phone → Build Number (apasă de mai multe ori)
3. Activează USB Debugging: Settings → Developer Options → USB Debugging
4. Modifică adresa IP din app/src/main/java/com/psimandan/neuread/voice/NeuTTSApiClient.kt 
5. În Android Studio: Run → Run 'app' 
7. APK-ul se va instala și lansa automat


### 3.3 Lansare Server NeuTTS

#### Metoda 1: Local
```bash
cd neutts-main
source venv/bin/activate  # activează environment virtual

# Pornire server
python server.py
```

Server disponibil la: `http://localhost:8000`  
API Documentation: `http://localhost:8000/docs` 

#### Metoda 2: Docker Build
```bash
# CPU
docker run --rm -p 8000:8000 \
  -e BACKBONE_PATH=/app/data/neutts-romanian-finetune/checkpoint-370000 \
  -v /host/path/to/models:/app/data:ro \
  neutts-server:latest

# GPU
docker run --rm --gpus all -p 8000:8000 \
  -e BACKBONE_DEVICE=cuda -e CODEC_DEVICE=cuda \
  -e BACKBONE_PATH=/app/data/neutts-romanian-finetune/checkpoint-370000 \
  -v /host/path/to/models:/app/data:ro \
  neutts-server:gpu
```

#### Metoda 3: Docker Compose
```bash
# CPU
docker-compose up

# GPU
docker-compose -f docker-compose.gpu.yml up
```

Server disponibil la: `http://localhost:8000`



### 3.7 Dezinstalare

```bash
# Dezinstalare prin ADB
adb uninstall com.psimandan.neuread

# Dezinstalare prin UI dispozitivului
Settings → Apps → NeuRead → Uninstall
```

---

## 4. Cerințe Sistem

### Pentru Dispozitiv Android
- **Recomandat:**
  - Android 16 (API Level 36)
  - Minimum 8GB RAM
  - Minimum 100 MB Stocare
  - Conexiune internet (pentru server backend)

### Pentru Server Backend
  
- **Recomandat (GPU):**
  - NVIDIA GPU cu 8GB+ VRAM
  - 32GB RAM
  - Minimum 5GB Stocare

---

## 5. Documentație Suplimentară

- `README.md` - Overview
- `neutts-main/TRAINING.md` - Ghid antrenare modele personalizate
- `neutts-main/examples/VOICE_CLONING_README.md` - Ghid tehnic clonare vocală
- `LICENSE` - MIT License

---

## 6. Contact și Suport

**Autor:** Șimandan Petra-Ariana

- Email: petra-ariana.simandan@student.upt.ro
- Repository Issues: https://github.com/numipasaaa/NeuRead/issues
- Discussion: https://github.com/numipasaaa/NeuRead/discussions

---

**Versiune:** 1.0

