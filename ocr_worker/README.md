# OCR Worker

This service hosts the OCR inference boundary for drawing text evidence.

It is intentionally separate from the Spring Boot backend, CAD Worker, and Vision Worker:

- backend keeps business workflow, rules, issues, evidence, and reports.
- cad_worker parses CAD structure from DXF/DWG.
- vision_worker detects visual symbols from rendered drawing images.
- ocr_worker extracts text regions from rendered drawing images.

## Run

Install Tesseract OCR first. Then install the Python API dependencies:

```powershell
pip install -r ocr_worker/requirements.txt
$env:TESSERACT_CMD="tesseract"
$env:OCR_LANG="eng"
python -m uvicorn ocr_worker.app.main:app --host 127.0.0.1 --port 9200
```

For Chinese drawing text, install the Tesseract `chi_sim` language data and set:

```powershell
$env:OCR_LANG="eng+chi_sim"
```

## Interfaces

- `GET /health`: service liveness.
- `GET /capabilities`: engine, license, command configuration, language, and output fields.
- `POST /ocr?confidence=0.5`: multipart PNG/JPG image input. Returns `regions`, `imageWidth`, `imageHeight`, `engine`, and `language`.

The Spring Boot backend stores `/ocr` results as version-level `OCR_TEXT` evidence through `POST /api/versions/{versionId}/ocr-recognize`.

The current OCR worker is a baseline open-source integration. It does not decide compliance by itself; OCR output must remain traceable evidence consumed by rules or reviewers.
