FROM python:3.14-slim

WORKDIR /app
RUN apt-get update \
    && apt-get install -y --no-install-recommends tesseract-ocr tesseract-ocr-eng tesseract-ocr-chi-sim \
    && rm -rf /var/lib/apt/lists/*
COPY ocr_worker/requirements.txt /app/ocr_worker/requirements.txt
RUN pip install --no-cache-dir -r /app/ocr_worker/requirements.txt
COPY ocr_worker /app/ocr_worker

EXPOSE 9200
CMD ["uvicorn", "ocr_worker.app.main:app", "--host", "0.0.0.0", "--port", "9200"]
