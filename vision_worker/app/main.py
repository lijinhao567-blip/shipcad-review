from __future__ import annotations

import os
from functools import lru_cache
from io import BytesIO
from pathlib import Path

from fastapi import FastAPI, File, HTTPException, UploadFile


app = FastAPI(title="ShipCAD YOLOv8 Vision Worker", version="0.1.0")


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok"}


@app.get("/capabilities")
def capabilities() -> dict:
    model_path = os.getenv("YOLO_MODEL_PATH", "")
    return {
        "engine": "ultralytics-yolov8",
        "license": "AGPL-3.0",
        "modelConfigured": bool(model_path),
        "modelPath": model_path,
        "outputs": ["bounding_box", "class_id", "class_name", "confidence"],
    }


@lru_cache(maxsize=1)
def load_model():
    model_path = os.getenv("YOLO_MODEL_PATH")
    if not model_path:
        raise RuntimeError("YOLO_MODEL_PATH is not configured.")
    if not Path(model_path).exists():
        raise RuntimeError(f"YOLO model file does not exist: {model_path}")
    try:
        from ultralytics import YOLO
    except ImportError as exc:
        raise RuntimeError("ultralytics is not installed. Install vision_worker requirements.") from exc
    return YOLO(model_path)


@app.post("/detect")
async def detect(file: UploadFile = File(...), confidence: float = 0.25) -> dict:
    if not file.filename or not file.filename.lower().endswith((".png", ".jpg", ".jpeg")):
        raise HTTPException(status_code=400, detail="Only PNG and JPG images are supported.")
    payload = await file.read()
    if len(payload) > 20 * 1024 * 1024:
        raise HTTPException(status_code=400, detail="Image exceeds 20MB limit.")

    try:
        from PIL import Image

        image = Image.open(BytesIO(payload)).convert("RGB")
        model = load_model()
        results = model.predict(image, conf=confidence, verbose=False)
    except RuntimeError as exc:
        raise HTTPException(status_code=503, detail=str(exc)) from exc
    except Exception as exc:
        raise HTTPException(status_code=422, detail=f"YOLOv8 detection failed: {exc}") from exc

    detections = []
    names = getattr(model, "names", {})
    for result in results:
        boxes = getattr(result, "boxes", None)
        if boxes is None:
            continue
        for box in boxes:
            class_id = int(box.cls[0].item())
            detections.append(
                {
                    "classId": class_id,
                    "className": names.get(class_id, str(class_id)) if isinstance(names, dict) else str(class_id),
                    "confidence": float(box.conf[0].item()),
                    "xyxy": [float(value) for value in box.xyxy[0].tolist()],
                }
            )
    return {"detections": detections}
