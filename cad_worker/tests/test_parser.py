from pathlib import Path

from cad_worker.app.parser import parse_dxf


def test_parse_valid_sample() -> None:
    parsed = parse_dxf(Path("samples/dxf/valid_ship_section.dxf"))
    assert parsed["summary"]["entityCount"] >= 5
    assert "TITLE" in parsed["summary"]["layers"]
    assert parsed["summary"]["typeCounts"]["TEXT"] >= 1


def test_parse_invalid_sample() -> None:
    parsed = parse_dxf(Path("samples/dxf/invalid_ship_section.dxf"))
    assert "bad-layer" in parsed["summary"]["layers"]
    assert "EMPTY-OLD" in parsed["summary"]["emptyLayers"]
