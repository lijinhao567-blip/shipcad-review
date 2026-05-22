from __future__ import annotations

import tempfile
from pathlib import Path

from fastapi import FastAPI, File, HTTPException, UploadFile

from .dwg_adapter import DwgAdapterUnavailable, parse_dwg
from .parser import parse_dxf


app = FastAPI(title="ShipCAD DXF Worker", version="0.1.0")


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok"}


@app.get("/capabilities")
def capabilities() -> dict:
    return {
        "formats": {
            "dxf": {"status": "enabled", "parser": "ezdxf"},
            "dwg": {"status": "optional", "parser": "LibreDWG dwg2dxf + ezdxf"},
        },
        "limits": {"maxFileSizeMb": 20},
    }


@app.post("/parse")
async def parse(file: UploadFile = File(...)) -> dict:
    if not file.filename or not file.filename.lower().endswith((".dxf", ".dwg")):
        raise HTTPException(status_code=400, detail="Only DXF and DWG files are supported")
    payload = await file.read()
    if len(payload) > 20 * 1024 * 1024:
        raise HTTPException(status_code=400, detail="File exceeds 20MB MVP limit")
    with tempfile.TemporaryDirectory() as temp_dir:
        path = Path(temp_dir) / file.filename
        path.write_bytes(payload)
        try:
            if path.suffix.lower() == ".dwg":
                return parse_dwg(path)
            return parse_dxf(path)
        except DwgAdapterUnavailable as exc:
            raise HTTPException(status_code=503, detail=str(exc)) from exc
        except Exception as exc:
            raise HTTPException(status_code=422, detail=f"CAD parse failed: {exc}") from exc
