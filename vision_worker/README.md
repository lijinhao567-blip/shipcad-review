# YOLOv8 Vision Worker

This service hosts the Ultralytics YOLOv8 inference boundary for drawing-symbol detection.

It is intentionally separate from the Spring Boot backend and CAD Worker:

- backend keeps business workflow, rules, issues, and reports.
- cad_worker parses CAD structure from DXF/DWG.
- vision_worker detects visual symbols from rendered drawing images.

## Run

```powershell
pip install -r vision_worker/requirements.txt
$env:YOLO_MODEL_PATH="models/best.pt"
python -m uvicorn vision_worker.app.main:app --host 127.0.0.1 --port 9100
```

The model file is not included in the repository. Train or download a YOLOv8 model that matches the project symbol taxonomy, then set `YOLO_MODEL_PATH`.

## Interfaces

- `GET /health`: service liveness.
- `GET /capabilities`: engine, license, model configuration, and output fields.
- `POST /detect?confidence=0.25`: multipart PNG/JPG image input. Returns `detections`, `imageWidth`, `imageHeight`, and `engine`.

The Spring Boot backend stores `/detect` results as version-level `YOLO_SYMBOL` evidence through `POST /api/versions/{versionId}/vision-detect`.
