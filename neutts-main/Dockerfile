# neutts-main/Dockerfile
FROM python:3.13-slim

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    APP_HOME=/app

WORKDIR $APP_HOME

# system deps needed for audio processing
RUN apt-get update && apt-get install -y --no-install-recommends \
    build-essential \
    git \
    ffmpeg \
    libsndfile1 \
    libopenblas-dev \
    espeak-ng \
    && rm -rf /var/lib/apt/lists/*

# copy only what we need early for caching
COPY neutts-main/requirements.txt ${APP_HOME}/

# Install python deps; adjust if you use pyproject/poetry
RUN pip install --upgrade pip \
 && if [ -f requirements.txt ]; then pip install -r requirements.txt; fi

# copy server code
COPY neutts-main/ ${APP_HOME}/

# create runtime directories
RUN mkdir -p /app/data /app/logs

ENV PORT=8000 \
    BACKBONE_PATH=/app/data/neutts-romanian-finetune/checkpoint-370000 \
    BACKBONE_DEVICE=cpu \
    CODEC_DEVICE=cpu

EXPOSE ${PORT}


CMD ["uvicorn", "server:app", "--host", "0.0.0.0", "--port", "8000", "--log-level", "info"]