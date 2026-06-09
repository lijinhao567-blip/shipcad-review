from __future__ import annotations

import hashlib
import json
import re
from dataclasses import dataclass
from pathlib import Path
from typing import Callable

import ezdxf


ROOT = Path(__file__).resolve().parents[1]
DATASET_DIR = ROOT / "datasets" / "parser"
CASE_DIR = DATASET_DIR / "cases"
MANIFEST_PATH = DATASET_DIR / "manifest.json"
FIXED_DXF_TIMESTAMP = "2451545.0"
FIXED_DXF_CREATED_BY = "1.4.4 @ 2000-01-01T00:00:00+00:00"
FIXED_DXF_GUID = "{00000000-0000-0000-0000-000000000000}"


@dataclass(frozen=True)
class ParserCase:
    case_id: str
    file_name: str
    title: str
    description: str
    builder: Callable[[Path], None]
    parser_expectations: dict[str, object]
    preview_expectations: dict[str, object]


def ensure_layer(doc: ezdxf.EzDxf, name: str, color: int = 7) -> None:
    if name not in doc.layers:
        doc.layers.add(name, color=color)


def save_deterministic(doc: ezdxf.EzDxf, path: Path) -> None:
    doc.header["$TDCREATE"] = float(FIXED_DXF_TIMESTAMP)
    doc.header["$TDUCREATE"] = float(FIXED_DXF_TIMESTAMP)
    doc.header["$TDUPDATE"] = float(FIXED_DXF_TIMESTAMP)
    doc.header["$TDUUPDATE"] = float(FIXED_DXF_TIMESTAMP)
    doc.header["$FINGERPRINTGUID"] = FIXED_DXF_GUID
    doc.header["$VERSIONGUID"] = FIXED_DXF_GUID
    doc.saveas(path)

    content = path.read_text(encoding="utf-8", errors="ignore")
    for variable in ("TDCREATE", "TDUCREATE", "TDUPDATE", "TDUUPDATE"):
        content = re.sub(rf"(\${variable}\n\s*40\n)[^\n]+", rf"\g<1>{FIXED_DXF_TIMESTAMP}", content)
    content = re.sub(
        r"\{[0-9A-Fa-f]{8}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{12}\}",
        FIXED_DXF_GUID,
        content,
    )
    content = re.sub(r"1\.4\.4 @ [0-9T:\-+.]+", FIXED_DXF_CREATED_BY, content)
    path.write_text(content, encoding="utf-8", newline="\n")


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def base_doc() -> ezdxf.EzDxf:
    doc = ezdxf.new("R2010")
    layers = {
        "TITLE": 7,
        "S-HULL": 1,
        "S-FRAME": 3,
        "S-STIFFENER": 4,
        "S-BULKHEAD": 5,
        "S-CENTER": 6,
        "DIM-MAIN": 2,
        "TEXT-NOTE": 7,
        "HATCH-SECTION": 8,
        "SYM-WELD": 1,
        "EQUIP-OUTLINE": 140,
    }
    for name, color in layers.items():
        ensure_layer(doc, name, color)
    add_title_block(doc)
    add_weld_symbol_block(doc)
    add_frame_mark_block(doc)
    return doc


def add_title_block(doc: ezdxf.EzDxf) -> None:
    block = doc.blocks.new("TITLE_BLOCK")
    block.add_lwpolyline([(0, 0), (90, 0), (90, 28), (0, 28), (0, 0)], dxfattribs={"layer": "TITLE"})
    block.add_line((0, 18), (90, 18), dxfattribs={"layer": "TITLE"})
    block.add_line((20, 0), (20, 28), dxfattribs={"layer": "TITLE"})
    block.add_attdef("DRAWING_NO", (24, 21), dxfattribs={"layer": "TITLE", "height": 2.5})
    block.add_attdef("REVISION", (24, 14), dxfattribs={"layer": "TITLE", "height": 2.5})
    block.add_attdef("SCALE", (24, 7), dxfattribs={"layer": "TITLE", "height": 2.5})


def add_weld_symbol_block(doc: ezdxf.EzDxf) -> None:
    block = doc.blocks.new("WELD_SYMBOL")
    block.add_line((0, 0), (10, 0), dxfattribs={"layer": "SYM-WELD"})
    block.add_line((4, 0), (2, -3), dxfattribs={"layer": "SYM-WELD"})
    block.add_line((4, 0), (6, -3), dxfattribs={"layer": "SYM-WELD"})
    block.add_attdef("WELD_CODE", (12, -1), dxfattribs={"layer": "SYM-WELD", "height": 1.8})


def add_frame_mark_block(doc: ezdxf.EzDxf) -> None:
    block = doc.blocks.new("FRAME_MARK")
    block.add_circle((0, 0), 3, dxfattribs={"layer": "S-FRAME"})
    block.add_attdef("FRAME_NO", (-2, -1), dxfattribs={"layer": "S-FRAME", "height": 1.6})


def add_title(msp: ezdxf.layouts.Modelspace, drawing_no: str, revision: str = "V1", scale: str = "1:50") -> None:
    insert = msp.add_blockref("TITLE_BLOCK", (250, -45), dxfattribs={"layer": "TITLE"})
    insert.add_attrib("DRAWING_NO", drawing_no, insert=(274, -24), dxfattribs={"layer": "TITLE", "height": 2.5})
    insert.add_attrib("REVISION", revision, insert=(274, -31), dxfattribs={"layer": "TITLE", "height": 2.5})
    insert.add_attrib("SCALE", scale, insert=(274, -38), dxfattribs={"layer": "TITLE", "height": 2.5})
    msp.add_text("ShipCAD synthetic parser baseline", dxfattribs={"layer": "TITLE", "height": 2.5}).set_placement((253, -15))


def add_dimension(msp: ezdxf.layouts.Modelspace, base: tuple[float, float], p1: tuple[float, float], p2: tuple[float, float], angle: float = 0) -> None:
    dimension = msp.add_linear_dim(base=base, p1=p1, p2=p2, angle=angle, dxfattribs={"layer": "DIM-MAIN"})
    dimension.render()


def add_hatch(msp: ezdxf.layouts.Modelspace, points: list[tuple[float, float]]) -> None:
    hatch = msp.add_hatch(color=8, dxfattribs={"layer": "HATCH-SECTION"})
    hatch.paths.add_polyline_path(points, is_closed=True)


def add_weld_symbol(msp: ezdxf.layouts.Modelspace, insert: tuple[float, float], code: str, rotation: float = 0) -> None:
    ref = msp.add_blockref("WELD_SYMBOL", insert, dxfattribs={"layer": "SYM-WELD", "rotation": rotation})
    ref.add_attrib("WELD_CODE", code, insert=(insert[0] + 12, insert[1] - 1), dxfattribs={"layer": "SYM-WELD", "height": 1.8})


def add_frame_mark(msp: ezdxf.layouts.Modelspace, insert: tuple[float, float], frame_no: str) -> None:
    ref = msp.add_blockref("FRAME_MARK", insert, dxfattribs={"layer": "S-FRAME"})
    ref.add_attrib("FRAME_NO", frame_no, insert=(insert[0] - 2, insert[1] - 1), dxfattribs={"layer": "S-FRAME", "height": 1.6})


def build_complex_ship_section(path: Path) -> None:
    doc = base_doc()
    msp = doc.modelspace()

    hull = [(0, 0), (24, 18), (78, 27), (145, 26), (202, 18), (230, 0), (205, -18), (118, -27), (32, -18), (0, 0)]
    msp.add_lwpolyline(hull, dxfattribs={"layer": "S-HULL"})
    msp.add_lwpolyline([(18, -10), (55, 9), (112, 15), (176, 10), (215, -9)], dxfattribs={"layer": "S-STIFFENER"})
    msp.add_arc((115, 0), 72, 16, 164, dxfattribs={"layer": "S-HULL"})
    msp.add_line((0, 0), (230, 0), dxfattribs={"layer": "S-CENTER"})
    msp.add_line((115, -30), (115, 32), dxfattribs={"layer": "S-CENTER"})

    for index, x in enumerate(range(20, 221, 20), start=1):
        msp.add_line((x, -17), (x, 22), dxfattribs={"layer": "S-FRAME"})
        add_frame_mark(msp, (x, 31), f"F{index:02d}")
    for y in (-16, -8, 8, 16):
        msp.add_line((18, y), (212, y), dxfattribs={"layer": "S-STIFFENER"})
    for x in range(35, 210, 35):
        msp.add_circle((x, 0), 3.5, dxfattribs={"layer": "EQUIP-OUTLINE"})

    add_hatch(msp, [(18, -10), (56, 8), (112, 14), (176, 9), (212, -8), (198, -16), (115, -24), (32, -16)])
    add_hatch(msp, [(90, -5), (140, -5), (140, 5), (90, 5)])

    for index, x in enumerate(range(35, 210, 35), start=1):
        add_weld_symbol(msp, (x - 8, 20), f"FW{index}", rotation=0)

    add_dimension(msp, (0, -42), (0, 0), (230, 0), 0)
    add_dimension(msp, (-16, 0), (0, -27), (0, 27), 90)
    add_dimension(msp, (115, 42), (20, 22), (220, 22), 0)
    msp.add_text("MAIN DECK", dxfattribs={"layer": "TEXT-NOTE", "height": 3.0}).set_placement((92, 23))
    msp.add_text("CENTERLINE", dxfattribs={"layer": "TEXT-NOTE", "height": 2.4}).set_placement((119, 2))
    msp.add_mtext("Synthetic section: frames, stiffeners, hatch and weld symbols", dxfattribs={"layer": "TEXT-NOTE", "char_height": 2.2}).set_location((12, -36))
    add_title(msp, "SYN-SECTION-001")
    save_deterministic(doc, path)


def build_dense_deck_grid(path: Path) -> None:
    doc = base_doc()
    msp = doc.modelspace()

    msp.add_lwpolyline([(0, 0), (360, 0), (360, 160), (0, 160), (0, 0)], dxfattribs={"layer": "S-HULL"})
    for x in range(0, 361, 12):
        layer = "S-FRAME" if x % 24 == 0 else "S-STIFFENER"
        msp.add_line((x, 0), (x, 160), dxfattribs={"layer": layer})
    for y in range(0, 161, 10):
        layer = "S-BULKHEAD" if y % 40 == 0 else "S-STIFFENER"
        msp.add_line((0, y), (360, y), dxfattribs={"layer": layer})
    for x in range(24, 349, 36):
        add_frame_mark(msp, (x, 170), f"D{x:03d}")
    for x in range(30, 340, 40):
        for y in range(25, 145, 40):
            msp.add_circle((x, y), 4.0, dxfattribs={"layer": "EQUIP-OUTLINE"})
            add_weld_symbol(msp, (x + 7, y + 7), "FW", rotation=45)
    for x in range(18, 330, 60):
        add_hatch(msp, [(x, 18), (x + 42, 18), (x + 42, 48), (x, 48)])
    for x in range(18, 330, 60):
        msp.add_lwpolyline([(x, 92), (x + 42, 92), (x + 42, 128), (x, 128), (x, 92)], dxfattribs={"layer": "S-HULL"})

    add_dimension(msp, (0, -22), (0, 0), (360, 0), 0)
    add_dimension(msp, (-24, 0), (0, 0), (0, 160), 90)
    add_dimension(msp, (0, 184), (0, 160), (360, 160), 0)
    add_dimension(msp, (384, 0), (360, 0), (360, 160), 90)
    for index, y in enumerate(range(20, 151, 30), start=1):
        msp.add_text(f"DECK LONGITUDINAL {index}", dxfattribs={"layer": "TEXT-NOTE", "height": 2.2}).set_placement((8, y + 2))
    msp.add_mtext("Synthetic deck grid: dense frames, equipment openings, hatch panels and repeated symbols", dxfattribs={"layer": "TEXT-NOTE", "char_height": 2.4}).set_location((110, -34))
    add_title(msp, "SYN-DECK-002", scale="1:100")
    save_deterministic(doc, path)


CASES = [
    ParserCase(
        case_id="complex_ship_section",
        file_name="complex_ship_section.dxf",
        title="Complex Ship Section",
        description="Synthetic mid-complexity ship section with hull contour, frames, stiffeners, hatches, dimensions, title attributes and weld symbols.",
        builder=build_complex_ship_section,
        parser_expectations={
            "minEntityCount": 70,
            "requiredLayers": ["TITLE", "S-HULL", "S-FRAME", "S-STIFFENER", "S-CENTER", "DIM-MAIN", "TEXT-NOTE", "HATCH-SECTION", "SYM-WELD", "EQUIP-OUTLINE"],
            "requiredBlocks": ["TITLE_BLOCK", "FRAME_MARK", "WELD_SYMBOL"],
            "requiredEntityTypes": ["LINE", "LWPOLYLINE", "ARC", "CIRCLE", "HATCH", "INSERT", "ATTRIB", "DIMENSION", "TEXT", "MTEXT"],
            "minTypeCounts": {"LINE": 17, "INSERT": 17, "ATTRIB": 19, "DIMENSION": 3, "HATCH": 2},
            "requiredTexts": ["MAIN DECK", "CENTERLINE", "Synthetic section"],
            "bounds": {"minWidth": 280, "minHeight": 70},
        },
        preview_expectations={
            "dxfViewerParser": "required",
            "minDxfViewerEntities": 55,
            "minLayerCount": 10,
            "requiresWebglSmoke": True,
        },
    ),
    ParserCase(
        case_id="dense_deck_grid",
        file_name="dense_deck_grid.dxf",
        title="Dense Deck Grid",
        description="Synthetic dense deck plan with repeated frames, longitudinal members, block references, equipment outlines, hatch panels, dimensions and notes.",
        builder=build_dense_deck_grid,
        parser_expectations={
            "minEntityCount": 150,
            "requiredLayers": ["TITLE", "S-HULL", "S-FRAME", "S-STIFFENER", "S-BULKHEAD", "DIM-MAIN", "TEXT-NOTE", "HATCH-SECTION", "SYM-WELD", "EQUIP-OUTLINE"],
            "requiredBlocks": ["TITLE_BLOCK", "FRAME_MARK", "WELD_SYMBOL"],
            "requiredEntityTypes": ["LINE", "LWPOLYLINE", "CIRCLE", "HATCH", "INSERT", "ATTRIB", "DIMENSION", "TEXT", "MTEXT"],
            "minTypeCounts": {"LINE": 48, "CIRCLE": 20, "INSERT": 35, "ATTRIB": 37, "DIMENSION": 4, "HATCH": 6},
            "requiredTexts": ["DECK LONGITUDINAL", "Synthetic deck grid"],
            "bounds": {"minWidth": 400, "minHeight": 220},
        },
        preview_expectations={
            "dxfViewerParser": "required",
            "minDxfViewerEntities": 130,
            "minLayerCount": 10,
            "requiresWebglSmoke": True,
        },
    ),
]


def build_manifest() -> dict[str, object]:
    samples = []
    for case in CASES:
        file_path = CASE_DIR / case.file_name
        samples.append(
            {
                "id": case.case_id,
                "file": f"cases/{case.file_name}",
                "format": "dxf",
                "title": case.title,
                "description": case.description,
                "source": "Self-created synthetic CAD fixture generated inside this repository.",
                "license": "AGPL-3.0-only",
                "generatedBy": "tools/generate_complex_dxf_dataset.py",
                "generationProfile": case.case_id,
                "sha256": sha256(file_path),
                "parserExpectations": case.parser_expectations,
                "previewExpectations": case.preview_expectations,
            }
        )
    return {
        "datasetVersion": "0.1.0",
        "generatedBy": "tools/generate_complex_dxf_dataset.py",
        "samples": samples,
    }


def main() -> None:
    CASE_DIR.mkdir(parents=True, exist_ok=True)
    for case in CASES:
        case.builder(CASE_DIR / case.file_name)
    MANIFEST_PATH.write_text(json.dumps(build_manifest(), indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(f"Generated {len(CASES)} complex parser cases in {DATASET_DIR}")


if __name__ == "__main__":
    main()
