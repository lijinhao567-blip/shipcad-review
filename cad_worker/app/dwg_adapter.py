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
    converter = os.getenv("LIBREDWG_DWG2DXF_BIN", "dwg2dxf")
    executable = shutil.which(converter)
    if executable is None:
        raise DwgAdapterUnavailable(
            "LibreDWG dwg2dxf was not found. Install libredwg tools or set LIBREDWG_DWG2DXF_BIN."
        )

    with tempfile.TemporaryDirectory() as temp_dir:
        output_dir = Path(temp_dir)
        command = [executable, "--overwrite", "--minimal", str(path)]
        result = subprocess.run(command, cwd=output_dir, capture_output=True, text=True, check=False)
        if result.returncode != 0:
            detail = (result.stderr or result.stdout or "unknown LibreDWG error").strip()
            raise DwgAdapterUnavailable(f"LibreDWG dwg2dxf failed: {detail}")

        candidates = sorted(output_dir.glob("*.dxf"))
        if not candidates:
            raise DwgAdapterUnavailable("LibreDWG dwg2dxf did not produce a DXF file.")
        parsed = parse_dxf(candidates[0])
        parsed["summary"]["parser"] = "libredwg-dwg2dxf+ezdxf"
        return parsed
