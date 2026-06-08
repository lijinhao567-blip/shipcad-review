from __future__ import annotations

import base64
import json
import tempfile
from pathlib import Path

from fastapi import FastAPI, File, HTTPException, Response, UploadFile

from .dwg_adapter import DwgAdapterUnavailable, convert_dwg_to_dxf, parse_dwg
from .parser import parse_dxf
from .renderer import DxfRenderUnavailable, render_dxf_to_png


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
        "rendering": {"status": "enabled", "renderer": "ezdxf-matplotlib", "formats": ["dxf", "dwg"]},
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


@app.post("/render")
async def render(
    file: UploadFile = File(...),
    width: int = 1600,
    height: int = 1200,
) -> Response:
    if not file.filename or not file.filename.lower().endswith((".dxf", ".dwg")):
        raise HTTPException(status_code=400, detail="Only DXF and DWG files are supported")
    payload = await file.read()
    if len(payload) > 20 * 1024 * 1024:
        raise HTTPException(status_code=400, detail="File exceeds 20MB MVP limit")
    with tempfile.TemporaryDirectory() as temp_dir:
        folder = Path(temp_dir)
        path = folder / file.filename
        path.write_bytes(payload)
        output = folder / "render.png"
        try:
            dxf_path = convert_dwg_to_dxf(path, folder / "converted") if path.suffix.lower() == ".dwg" else path
            metadata = render_dxf_to_png(dxf_path, output, width=width, height=height)
            encoded_metadata = base64.urlsafe_b64encode(
                json.dumps(metadata, separators=(",", ":")).encode("utf-8")
            ).decode("ascii")
            return Response(
                content=output.read_bytes(),
                media_type="image/png",
                headers={"X-ShipCAD-Render-Metadata": encoded_metadata},
            )
        except DwgAdapterUnavailable as exc:
            raise HTTPException(status_code=503, detail=str(exc)) from exc
        except DxfRenderUnavailable as exc:
            raise HTTPException(status_code=503, detail=str(exc)) from exc
        except Exception as exc:
            raise HTTPException(status_code=422, detail=f"CAD render failed: {exc}") from exc
