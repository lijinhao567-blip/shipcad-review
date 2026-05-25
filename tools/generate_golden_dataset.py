from __future__ import annotations

import json
from dataclasses import dataclass
from pathlib import Path
from typing import Callable

import ezdxf


ROOT = Path(__file__).resolve().parents[1]
DATASET_DIR = ROOT / "datasets" / "rules"
CASE_DIR = DATASET_DIR / "cases"
MANIFEST_PATH = DATASET_DIR / "expected.json"


@dataclass(frozen=True)
class Case:
    case_id: str
    file_name: str
    version_no: str
    expected_rule_codes: list[str]
    builder: Callable[[Path], None]
    min_entity_count: int
    required_layers: list[str]
    required_blocks: list[str]
    description: str


def ensure_layer(doc: ezdxf.EzDxf, name: str) -> None:
    if name not in doc.layers:
        doc.layers.add(name)


def base_doc(*, with_title: bool = True) -> ezdxf.EzDxf:
    doc = ezdxf.new("R2010")
    layers = ["S-HULL", "DIM-MAIN", "TEXT-NOTE"]
    if with_title:
        layers.insert(0, "TITLE")
    for layer in layers:
        ensure_layer(doc, layer)
    if with_title:
        doc.blocks.new("TITLE_BLOCK")
    return doc


def add_title(msp: ezdxf.layouts.Modelspace) -> None:
    msp.add_blockref("TITLE_BLOCK", (0, 0), dxfattribs={"layer": "TITLE"})
    msp.add_text("Drawing No A22-GOLDEN-001 Rev V1", dxfattribs={"layer": "TITLE", "height": 2.5}).set_placement((8, 8))


def add_standard_geometry(msp: ezdxf.layouts.Modelspace) -> None:
    msp.add_line((0, 0), (120, 0), dxfattribs={"layer": "S-HULL"})
    msp.add_line((120, 0), (120, 60), dxfattribs={"layer": "S-HULL"})
    msp.add_circle((40, 20), 4, dxfattribs={"layer": "S-HULL"})
    msp.add_text("Main deck clearance 600", dxfattribs={"layer": "DIM-MAIN", "height": 2.0}).set_placement((50, 5))
    msp.add_text("General note checked", dxfattribs={"layer": "TEXT-NOTE", "height": 2.0}).set_placement((15, 40))


def build_compliant(path: Path) -> None:
    doc = base_doc()
    msp = doc.modelspace()
    add_title(msp)
    add_standard_geometry(msp)
    doc.saveas(path)


def build_invalid_layer(path: Path) -> None:
    doc = base_doc()
    ensure_layer(doc, "BAD-LAYER")
    msp = doc.modelspace()
    add_title(msp)
    add_standard_geometry(msp)
    msp.add_line((10, 10), (20, 15), dxfattribs={"layer": "BAD-LAYER"})
    doc.saveas(path)


def build_empty_layer(path: Path) -> None:
    doc = base_doc()
    ensure_layer(doc, "S-EMPTY")
    msp = doc.modelspace()
    add_title(msp)
    add_standard_geometry(msp)
    doc.saveas(path)


def build_missing_title(path: Path) -> None:
    doc = base_doc(with_title=False)
    msp = doc.modelspace()
    add_standard_geometry(msp)
    doc.saveas(path)


def build_placeholder_text(path: Path) -> None:
    doc = base_doc()
    msp = doc.modelspace()
    add_title(msp)
    add_standard_geometry(msp)
    msp.add_text("TBD bracket opening", dxfattribs={"layer": "TEXT-NOTE", "height": 2.0}).set_placement((20, 46))
    doc.saveas(path)


def build_low_density(path: Path) -> None:
    doc = ezdxf.new("R2010")
    ensure_layer(doc, "TITLE")
    ensure_layer(doc, "S-HULL")
    doc.blocks.new("TITLE_BLOCK")
    msp = doc.modelspace()
    msp.add_blockref("TITLE_BLOCK", (0, 0), dxfattribs={"layer": "TITLE"})
    msp.add_line((0, 0), (10, 0), dxfattribs={"layer": "S-HULL"})
    doc.saveas(path)


CASES = [
    Case(
        case_id="compliant_section",
        file_name="compliant_section.dxf",
        version_no="V1",
        expected_rule_codes=[],
        builder=build_compliant,
        min_entity_count=6,
        required_layers=["TITLE", "S-HULL", "DIM-MAIN", "TEXT-NOTE"],
        required_blocks=["TITLE_BLOCK"],
        description="Baseline DXF with accepted layers, title block, enough entities, and no placeholder text.",
    ),
    Case(
        case_id="invalid_layer_name",
        file_name="invalid_layer_name.dxf",
        version_no="V1",
        expected_rule_codes=["LAYER_NAME_STANDARD"],
        builder=build_invalid_layer,
        min_entity_count=7,
        required_layers=["BAD-LAYER"],
        required_blocks=["TITLE_BLOCK"],
        description="Contains one custom layer that violates the configured layer prefix convention.",
    ),
    Case(
        case_id="empty_custom_layer",
        file_name="empty_custom_layer.dxf",
        version_no="V1",
        expected_rule_codes=["EMPTY_LAYER_CHECK"],
        builder=build_empty_layer,
        min_entity_count=6,
        required_layers=["S-EMPTY"],
        required_blocks=["TITLE_BLOCK"],
        description="Declares a non-system layer with no entities.",
    ),
    Case(
        case_id="missing_title_block",
        file_name="missing_title_block.dxf",
        version_no="V1",
        expected_rule_codes=["TITLE_BLOCK_REQUIRED"],
        builder=build_missing_title,
        min_entity_count=5,
        required_layers=["S-HULL", "DIM-MAIN", "TEXT-NOTE"],
        required_blocks=[],
        description="Has drawing geometry but no title block or title keywords.",
    ),
    Case(
        case_id="placeholder_text",
        file_name="placeholder_text.dxf",
        version_no="V1",
        expected_rule_codes=["TEXT_PLACEHOLDER"],
        builder=build_placeholder_text,
        min_entity_count=7,
        required_layers=["TEXT-NOTE"],
        required_blocks=["TITLE_BLOCK"],
        description="Contains one TBD note that should be reviewed before delivery.",
    ),
    Case(
        case_id="low_entity_density",
        file_name="low_entity_density.dxf",
        version_no="V1",
        expected_rule_codes=["ENTITY_DENSITY"],
        builder=build_low_density,
        min_entity_count=2,
        required_layers=["TITLE", "S-HULL"],
        required_blocks=["TITLE_BLOCK"],
        description="Title block exists, but the drawing has too few model-space entities.",
    ),
    Case(
        case_id="invalid_version_format",
        file_name="invalid_version_format.dxf",
        version_no="draft",
        expected_rule_codes=["VERSION_FORMAT"],
        builder=build_compliant,
        min_entity_count=6,
        required_layers=["TITLE", "S-HULL", "DIM-MAIN", "TEXT-NOTE"],
        required_blocks=["TITLE_BLOCK"],
        description="Geometry is valid, but the uploaded version number is not traceable.",
    ),
]


def build_manifest() -> list[dict[str, object]]:
    manifest = []
    for case in CASES:
        manifest.append(
            {
                "id": case.case_id,
                "file": f"cases/{case.file_name}",
                "format": "dxf",
                "drawingNo": f"GOLDEN-{case.case_id.upper().replace('-', '_')}",
                "title": case.case_id.replace("_", " ").title(),
                "versionNo": case.version_no,
                "expectedRuleCodes": case.expected_rule_codes,
                "allowUnexpectedRuleCodes": False,
                "parserExpectations": {
                    "minEntityCount": case.min_entity_count,
                    "requiredLayers": case.required_layers,
                    "requiredBlocks": case.required_blocks,
                },
                "description": case.description,
            }
        )
    return manifest


def main() -> None:
    CASE_DIR.mkdir(parents=True, exist_ok=True)
    for case in CASES:
        case.builder(CASE_DIR / case.file_name)
    MANIFEST_PATH.write_text(json.dumps(build_manifest(), indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(f"Generated {len(CASES)} golden cases in {DATASET_DIR}")


if __name__ == "__main__":
    main()
