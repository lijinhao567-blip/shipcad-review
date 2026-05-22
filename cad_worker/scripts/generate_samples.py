from __future__ import annotations

from pathlib import Path

import ezdxf


ROOT = Path(__file__).resolve().parents[2]
SAMPLE_DIR = ROOT / "samples" / "dxf"


def build_valid(path: Path) -> None:
    doc = ezdxf.new("R2010")
    for layer in ["TITLE", "S-HULL", "DIM-MAIN", "TEXT-NOTE"]:
        if layer not in doc.layers:
            doc.layers.add(layer)
    msp = doc.modelspace()
    doc.blocks.new("TITLE_BLOCK")
    msp.add_blockref("TITLE_BLOCK", (0, 0), dxfattribs={"layer": "TITLE"})
    msp.add_text("图号 A22-SEC-001 版次 V1", dxfattribs={"layer": "TITLE", "height": 2.5}).set_placement((10, 10))
    msp.add_line((0, 0), (120, 0), dxfattribs={"layer": "S-HULL"})
    msp.add_line((120, 0), (120, 60), dxfattribs={"layer": "S-HULL"})
    msp.add_circle((40, 20), 4, dxfattribs={"layer": "S-HULL"})
    msp.add_text("主甲板净距 600", dxfattribs={"layer": "DIM-MAIN", "height": 2.0}).set_placement((50, 5))
    doc.saveas(path)


def build_invalid(path: Path) -> None:
    doc = ezdxf.new("R2010")
    for layer in ["bad-layer", "EMPTY-OLD"]:
        if layer not in doc.layers:
            doc.layers.add(layer)
    msp = doc.modelspace()
    msp.add_line((0, 0), (10, 0), dxfattribs={"layer": "bad-layer"})
    msp.add_text("TBD 待定开孔", dxfattribs={"layer": "bad-layer", "height": 2.5}).set_placement((5, 5))
    doc.saveas(path)


if __name__ == "__main__":
    SAMPLE_DIR.mkdir(parents=True, exist_ok=True)
    build_valid(SAMPLE_DIR / "valid_ship_section.dxf")
    build_invalid(SAMPLE_DIR / "invalid_ship_section.dxf")
    print(SAMPLE_DIR)
