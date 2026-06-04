from __future__ import annotations

import os
import shutil
import subprocess
import tempfile
from pathlib import Path

from .parser import parse_dxf


class DwgAdapterUnavailable(RuntimeError):
    """Raised when LibreDWG command-line tools are not available."""


def parse_dwg(path: Path) -> dict:
    with tempfile.TemporaryDirectory() as temp_dir:
        dxf_path = convert_dwg_to_dxf(path, Path(temp_dir))
        parsed = parse_dxf(dxf_path)
        parsed["summary"]["parser"] = "libredwg-dwg2dxf+ezdxf"
        return parsed


def convert_dwg_to_dxf(path: Path, output_dir: Path) -> Path:
    converter = os.getenv("LIBREDWG_DWG2DXF_BIN", "dwg2dxf")
    executable = shutil.which(converter)
    if executable is None:
        raise DwgAdapterUnavailable(
            "LibreDWG dwg2dxf was not found. Install libredwg tools or set LIBREDWG_DWG2DXF_BIN."
        )

    output_dir.mkdir(parents=True, exist_ok=True)
    command = [executable, "--overwrite", "--minimal", str(path)]
    result = subprocess.run(command, cwd=output_dir, capture_output=True, text=True, check=False)
    if result.returncode != 0:
        detail = (result.stderr or result.stdout or "unknown LibreDWG error").strip()
        raise DwgAdapterUnavailable(f"LibreDWG dwg2dxf failed: {detail}")

    candidates = sorted(output_dir.glob("*.dxf"))
    if not candidates:
        raise DwgAdapterUnavailable("LibreDWG dwg2dxf did not produce a DXF file.")
    return candidates[0]
