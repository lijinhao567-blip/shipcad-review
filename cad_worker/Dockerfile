FROM python:3.13-slim

WORKDIR /app
ENV PYTHONPATH=/app

COPY cad_worker/requirements.txt /app/cad_worker/requirements.txt
RUN pip install --no-cache-dir -r /app/cad_worker/requirements.txt

COPY cad_worker /app/cad_worker

EXPOSE 9000
CMD ["uvicorn", "cad_worker.app.main:app", "--host", "0.0.0.0", "--port", "9000"]
