FROM python:3.13-slim

WORKDIR /app
ENV PYTHONPATH=/app

COPY vision_worker/requirements.txt /app/vision_worker/requirements.txt
RUN pip install --no-cache-dir -r /app/vision_worker/requirements.txt

COPY vision_worker /app/vision_worker

EXPOSE 9100
CMD ["uvicorn", "vision_worker.app.main:app", "--host", "0.0.0.0", "--port", "9100"]
