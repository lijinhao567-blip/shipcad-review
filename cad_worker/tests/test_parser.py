from pathlib import Path

import ezdxf

from cad_worker.app.parser import parse_dxf


def test_parse_valid_sample() -> None:
    parsed = parse_dxf(Path("samples/dxf/valid_ship_section.dxf"))
    assert parsed["summary"]["entityCount"] >= 5
    assert "TITLE" in parsed["summary"]["layers"]
    assert parsed["summary"]["typeCounts"]["TEXT"] >= 1
    assert all(entity["handle"] for entity in parsed["entities"])
    assert any(entity["geometry"].get("bounds") for entity in parsed["entities"])


def test_parse_invalid_sample() -> None:
    parsed = parse_dxf(Path("samples/dxf/invalid_ship_section.dxf"))
    assert "bad-layer" in parsed["summary"]["layers"]
    assert "EMPTY-OLD" in parsed["summary"]["emptyLayers"]


def test_parse_dimension_and_insert_attributes(tmp_path: Path) -> None:
    doc = ezdxf.new("R2010")
    doc.layers.add("TITLE")
    doc.layers.add("DIM-MAIN")
    block = doc.blocks.new("TITLE_BLOCK")
    block.add_attdef("DRAWING_NO", (0, 0), dxfattribs={"layer": "TITLE", "height": 2.5})

    msp = doc.modelspace()
    insert = msp.add_blockref("TITLE_BLOCK", (0, 0), dxfattribs={"layer": "TITLE"})
    insert.add_attrib("DRAWING_NO", "A22-001", insert=(8, 8), dxfattribs={"layer": "TITLE", "height": 2.5})
    dimension = msp.add_linear_dim(
        base=(0, -8),
        p1=(0, 0),
        p2=(120, 0),
        angle=0,
        dxfattribs={"layer": "DIM-MAIN"},
    )
    dimension.render()

    path = tmp_path / "dimension_attrib.dxf"
    doc.saveas(path)

    parsed = parse_dxf(path)
    assert parsed["summary"]["typeCounts"]["ATTRIB"] == 1
    assert parsed["summary"]["typeCounts"]["DIMENSION"] == 1

    attribute = next(entity for entity in parsed["entities"] if entity["entityType"] == "ATTRIB")
    assert attribute["text"] == "A22-001"
    assert attribute["blockName"] == "TITLE_BLOCK"
    assert attribute["geometry"]["tag"] == "DRAWING_NO"

    dimension_entity = next(entity for entity in parsed["entities"] if entity["entityType"] == "DIMENSION")
    assert dimension_entity["text"] == "120.0"
    assert dimension_entity["geometry"]["measurement"] == 120.0
    assert dimension_entity["geometry"]["extensionLine2"] == [120.0, 0.0]
