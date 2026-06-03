from __future__ import annotations

import csv
import os
import shutil
import subprocess
import tempfile
from io import BytesIO
from pathlib import Path

from fastapi import FastAPI, File, HTTPException, UploadFile


app = FastAPI(title="ShipCAD OCR Worker", version="0.1.0")

SUPPORTED_IMAGE_EXTENSIONS = (".png", ".jpg", ".jpeg")


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok"}


@app.get("/capabilities")
def capabilities() -> dict:
    command = tesseract_command()
    return {
        "engine": "tesseract",
        "license": "Apache-2.0",
        "command": command,
        "commandAvailable": command_available(command),
        "language": ocr_language(),
        "outputs": ["text", "bounding_box", "confidence", "image_size"],
    }


@app.post("/ocr")
async def recognize(file: UploadFile = File(...), confidence: float = 0.5) -> dict:
    if confidence < 0 or confidence > 1:
        raise HTTPException(status_code=400, detail="confidence must be between 0 and 1.")
    if not file.filename or not file.filename.lower().endswith(SUPPORTED_IMAGE_EXTENSIONS):
        raise HTTPException(status_code=400, detail="Only PNG and JPG images are supported.")
    payload = await file.read()
    if len(payload) > 20 * 1024 * 1024:
        raise HTTPException(status_code=400, detail="Image exceeds 20MB limit.")

    command = tesseract_command()
    if not command_available(command):
        raise HTTPException(status_code=503, detail=f"Tesseract OCR is not configured or not found: {command}")

    temp_path: Path | None = None
    try:
        from PIL import Image

        image = Image.open(BytesIO(payload)).convert("RGB")
        width, height = image.size
        with tempfile.NamedTemporaryFile(delete=False, suffix=".png") as temp_file:
            temp_path = Path(temp_file.name)
        image.save(temp_path)
        output = run_tesseract(command, temp_path)
        return {
            "regions": parse_tesseract_tsv(output, confidence, ocr_language()),
            "imageWidth": width,
            "imageHeight": height,
            "engine": "tesseract",
            "language": ocr_language(),
        }
    except RuntimeError as exc:
        raise HTTPException(status_code=503, detail=str(exc)) from exc
    except Exception as exc:
        raise HTTPException(status_code=422, detail=f"OCR recognition failed: {exc}") from exc
    finally:
        if temp_path is not None:
            temp_path.unlink(missing_ok=True)


def tesseract_command() -> str:
    return os.getenv("TESSERACT_CMD", "tesseract")


def ocr_language() -> str:
    return os.getenv("OCR_LANG", "eng")


def command_available(command: str) -> bool:
    path = Path(command)
    return path.exists() or shutil.which(command) is not None


def run_tesseract(command: str, image_path: Path) -> str:
    timeout = int(os.getenv("OCR_TIMEOUT_SECONDS", "30"))
    psm = os.getenv("TESSERACT_PSM", "6")
    process = subprocess.run(
        [command, str(image_path), "stdout", "-l", ocr_language(), "--psm", psm, "tsv"],
        capture_output=True,
        check=False,
        text=True,
        timeout=timeout,
    )
    if process.returncode != 0:
        detail = process.stderr.strip() or process.stdout.strip() or f"exit code {process.returncode}"
        raise RuntimeError(f"Tesseract OCR failed: {detail}")
    return process.stdout


def parse_tesseract_tsv(output: str, min_confidence: float, language: str) -> list[dict]:
    regions: list[dict] = []
    reader = csv.DictReader(output.splitlines(), delimiter="\t")
    for row in reader:
        text = (row.get("text") or "").strip()
        if not text:
            continue
        try:
            confidence = float(row.get("conf") or -1)
        except ValueError:
            continue
        if confidence < 0:
            continue
        normalized_confidence = round(max(0.0, min(confidence / 100.0, 1.0)), 4)
        if normalized_confidence < min_confidence:
            continue
        try:
            left = float(row.get("left") or 0)
            top = float(row.get("top") or 0)
            width = float(row.get("width") or 0)
            height = float(row.get("height") or 0)
        except ValueError:
            continue
        regions.append(
            {
                "text": text,
                "confidence": normalized_confidence,
                "xyxy": [left, top, left + width, top + height],
                "language": language,
            }
        )
    return regions
