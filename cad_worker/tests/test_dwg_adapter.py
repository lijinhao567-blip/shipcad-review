from __future__ import annotations

import pytest

from cad_worker.app.dwg_adapter import DwgAdapterUnavailable, parse_dwg


def test_dwg_adapter_reports_missing_libredwg(monkeypatch, tmp_path):
    monkeypatch.setenv("LIBREDWG_DWG2DXF_BIN", "definitely_missing_dwg2dxf")
    dwg = tmp_path / "sample.dwg"
    dwg.write_bytes(b"not a real dwg")

    with pytest.raises(DwgAdapterUnavailable, match="LibreDWG dwg2dxf was not found"):
        parse_dwg(dwg)
