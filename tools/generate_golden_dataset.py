from __future__ import annotations

import json
import re
from dataclasses import dataclass, field
from pathlib import Path
from typing import Callable

import ezdxf


ROOT = Path(__file__).resolve().parents[1]
DATASET_DIR = ROOT / "datasets" / "rules"
CASE_DIR = DATASET_DIR / "cases"
MANIFEST_PATH = DATASET_DIR / "expected.json"
FIXED_DXF_TIMESTAMP = "2451545.0"
FIXED_DXF_CREATED_BY = "1.4.4 @ 2000-01-01T00:00:00+00:00"
FIXED_DXF_GUID = "{00000000-0000-0000-0000-000000000000}"


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
    required_entity_types: list[str] = field(default_factory=list)
    expected_evidence: dict[str, dict[str, object]] = field(default_factory=dict)
    review_task: dict[str, object] = field(default_factory=dict)
    mock_vision: dict[str, object] = field(default_factory=dict)
    mock_ocr: dict[str, object] = field(default_factory=dict)


def ensure_layer(doc: ezdxf.EzDxf, name: str) -> None:
    if name not in doc.layers:
        doc.layers.add(name)


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
    content = re.sub(r"\{[0-9A-Fa-f]{8}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{12}\}", FIXED_DXF_GUID, content)
    content = re.sub(r"1\.4\.4 @ [0-9T:\-+.]+", FIXED_DXF_CREATED_BY, content)
    path.write_text(content, encoding="utf-8", newline="\n")


def base_doc(*, with_title: bool = True) -> ezdxf.EzDxf:
    doc = ezdxf.new("R2010")
    layers = ["S-HULL", "DIM-MAIN", "TEXT-NOTE"]
    if with_title:
        layers.insert(0, "TITLE")
    for layer in layers:
        ensure_layer(doc, layer)
    if with_title:
        title_block = doc.blocks.new("TITLE_BLOCK")
        title_block.add_attdef("DRAWING_NO", (0, 0), dxfattribs={"layer": "TITLE", "height": 2.5})
        title_block.add_attdef("REVISION", (0, -4), dxfattribs={"layer": "TITLE", "height": 2.5})
    return doc


def add_title(
    msp: ezdxf.layouts.Modelspace,
    *,
    drawing_no: str = "A22-GOLDEN-001",
    revision: str = "V1",
    include_drawing_no: bool = True,
    include_revision: bool = True,
) -> None:
    insert = msp.add_blockref("TITLE_BLOCK", (0, 0), dxfattribs={"layer": "TITLE"})
    if include_drawing_no:
        insert.add_attrib("DRAWING_NO", drawing_no, insert=(8, 8), dxfattribs={"layer": "TITLE", "height": 2.5})
    if include_revision:
        insert.add_attrib("REVISION", revision, insert=(8, 5), dxfattribs={"layer": "TITLE", "height": 2.5})
    msp.add_text(f"Drawing No {drawing_no} Rev {revision}", dxfattribs={"layer": "TITLE", "height": 2.5}).set_placement((8, 8))


def add_standard_geometry(
    msp: ezdxf.layouts.Modelspace,
    *,
    include_dimension: bool = True,
    dimension_layer: str = "DIM-MAIN",
) -> None:
    msp.add_line((0, 0), (120, 0), dxfattribs={"layer": "S-HULL"})
    msp.add_line((120, 0), (120, 60), dxfattribs={"layer": "S-HULL"})
    msp.add_circle((40, 20), 4, dxfattribs={"layer": "S-HULL"})
    msp.add_text("Main deck clearance 600", dxfattribs={"layer": "DIM-MAIN", "height": 2.0}).set_placement((50, 5))
    msp.add_text("General note checked", dxfattribs={"layer": "TEXT-NOTE", "height": 2.0}).set_placement((15, 40))
    if not include_dimension:
        return
    dimension = msp.add_linear_dim(
        base=(0, -8),
        p1=(0, 0),
        p2=(120, 0),
        angle=0,
        dxfattribs={"layer": dimension_layer},
    )
    dimension.render()


def build_compliant(path: Path) -> None:
    doc = base_doc()
    msp = doc.modelspace()
    add_title(msp)
    add_standard_geometry(msp)
    save_deterministic(doc, path)


def build_invalid_layer(path: Path) -> None:
    doc = base_doc()
    ensure_layer(doc, "BAD-LAYER")
    msp = doc.modelspace()
    add_title(msp)
    add_standard_geometry(msp)
    msp.add_line((10, 10), (20, 15), dxfattribs={"layer": "BAD-LAYER"})
    save_deterministic(doc, path)


def build_empty_layer(path: Path) -> None:
    doc = base_doc()
    ensure_layer(doc, "S-EMPTY")
    msp = doc.modelspace()
    add_title(msp)
    add_standard_geometry(msp)
    save_deterministic(doc, path)


def build_missing_title(path: Path) -> None:
    doc = base_doc(with_title=False)
    msp = doc.modelspace()
    add_standard_geometry(msp)
    save_deterministic(doc, path)


def build_placeholder_text(path: Path) -> None:
    doc = base_doc()
    msp = doc.modelspace()
    add_title(msp)
    add_standard_geometry(msp)
    msp.add_text("TBD bracket opening", dxfattribs={"layer": "TEXT-NOTE", "height": 2.0}).set_placement((20, 46))
    save_deterministic(doc, path)


def build_missing_title_attribute(path: Path) -> None:
    doc = base_doc()
    msp = doc.modelspace()
    add_title(msp, include_revision=False)
    add_standard_geometry(msp)
    save_deterministic(doc, path)


def build_version_mismatch(path: Path) -> None:
    doc = base_doc()
    msp = doc.modelspace()
    add_title(msp, revision="V2")
    add_standard_geometry(msp)
    save_deterministic(doc, path)


def build_missing_dimension(path: Path) -> None:
    doc = base_doc()
    msp = doc.modelspace()
    add_title(msp)
    add_standard_geometry(msp, include_dimension=False)
    save_deterministic(doc, path)


def build_dimension_wrong_layer(path: Path) -> None:
    doc = base_doc()
    msp = doc.modelspace()
    add_title(msp)
    add_standard_geometry(msp, dimension_layer="S-HULL")
    save_deterministic(doc, path)


def build_low_density(path: Path) -> None:
    doc = ezdxf.new("R2010")
    ensure_layer(doc, "TITLE")
    ensure_layer(doc, "S-HULL")
    doc.blocks.new("TITLE_BLOCK")
    msp = doc.modelspace()
    msp.add_blockref("TITLE_BLOCK", (0, 0), dxfattribs={"layer": "TITLE"})
    msp.add_line((0, 0), (10, 0), dxfattribs={"layer": "S-HULL"})
    save_deterministic(doc, path)


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
        required_entity_types=["DIMENSION", "ATTRIB"],
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
        expected_evidence={"LAYER_NAME_STANDARD": {"layerName": "BAD-LAYER", "requireEntityRef": True}},
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
        expected_evidence={"EMPTY_LAYER_CHECK": {"layerName": "S-EMPTY", "requireEntityRef": False}},
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
        required_entity_types=["DIMENSION"],
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
        expected_evidence={"TEXT_PLACEHOLDER": {"layerName": "TEXT-NOTE", "requireEntityRef": True}},
    ),
    Case(
        case_id="missing_title_attribute",
        file_name="missing_title_attribute.dxf",
        version_no="V1",
        expected_rule_codes=["TITLE_ATTRIBUTE_REQUIRED"],
        builder=build_missing_title_attribute,
        min_entity_count=6,
        required_layers=["TITLE", "S-HULL", "DIM-MAIN", "TEXT-NOTE"],
        required_blocks=["TITLE_BLOCK"],
        description="Title block exists, but the inserted block reference omits the REVISION attribute.",
        required_entity_types=["DIMENSION", "ATTRIB"],
        expected_evidence={"TITLE_ATTRIBUTE_REQUIRED": {"layerName": "TITLE", "requireEntityRef": False}},
    ),
    Case(
        case_id="version_mismatch",
        file_name="version_mismatch.dxf",
        version_no="V1",
        expected_rule_codes=["VERSION_TITLE_CONSISTENCY"],
        builder=build_version_mismatch,
        min_entity_count=6,
        required_layers=["TITLE", "S-HULL", "DIM-MAIN", "TEXT-NOTE"],
        required_blocks=["TITLE_BLOCK"],
        description="System version is V1, but the title block REVISION attribute is V2.",
        required_entity_types=["DIMENSION", "ATTRIB"],
        expected_evidence={"VERSION_TITLE_CONSISTENCY": {"layerName": "TITLE", "requireEntityRef": True}},
    ),
    Case(
        case_id="missing_dimension",
        file_name="missing_dimension.dxf",
        version_no="V1",
        expected_rule_codes=["DIMENSION_REQUIRED"],
        builder=build_missing_dimension,
        min_entity_count=5,
        required_layers=["TITLE", "S-HULL", "DIM-MAIN", "TEXT-NOTE"],
        required_blocks=["TITLE_BLOCK"],
        description="Drawing has enough geometry and title attributes, but no DIMENSION entity.",
        required_entity_types=["ATTRIB"],
        expected_evidence={"DIMENSION_REQUIRED": {"requireEntityRef": False}},
    ),
    Case(
        case_id="dimension_wrong_layer",
        file_name="dimension_wrong_layer.dxf",
        version_no="V1",
        expected_rule_codes=["DIMENSION_LAYER_STANDARD"],
        builder=build_dimension_wrong_layer,
        min_entity_count=6,
        required_layers=["TITLE", "S-HULL", "DIM-MAIN", "TEXT-NOTE"],
        required_blocks=["TITLE_BLOCK"],
        description="Contains a DIMENSION entity placed on S-HULL instead of a DIM-* layer.",
        required_entity_types=["DIMENSION", "ATTRIB"],
        expected_evidence={"DIMENSION_LAYER_STANDARD": {"layerName": "S-HULL", "requireEntityRef": True}},
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
        required_entity_types=["DIMENSION", "ATTRIB"],
    ),
    Case(
        case_id="multimodal_clean_evidence",
        file_name="multimodal_clean_evidence.dxf",
        version_no="V1",
        expected_rule_codes=[],
        builder=build_compliant,
        min_entity_count=6,
        required_layers=["TITLE", "S-HULL", "DIM-MAIN", "TEXT-NOTE"],
        required_blocks=["TITLE_BLOCK"],
        description="Compliant CAD plus clean mock Vision/OCR evidence; cross-modal rules must not fire.",
        required_entity_types=["DIMENSION", "ATTRIB"],
        review_task={"autoVision": True, "autoOcr": True, "visionConfidence": 0.25, "ocrConfidence": 0.5},
        mock_vision={
            "detections": [
                {
                    "classId": 1,
                    "className": "title_block",
                    "confidence": 0.93,
                    "xyxy": [120.0, 80.0, 460.0, 220.0],
                }
            ],
            "imageWidth": 640,
            "imageHeight": 480,
            "engine": "mock-yolov8",
        },
        mock_ocr={
            "regions": [
                {
                    "text": "APPROVED DETAIL",
                    "confidence": 0.92,
                    "xyxy": [24.0, 32.0, 260.0, 68.0],
                    "language": "eng",
                }
            ],
            "imageWidth": 640,
            "imageHeight": 480,
            "engine": "mock-ocr",
            "language": "eng",
        },
    ),
    Case(
        case_id="multimodal_ocr_yolo_conflict",
        file_name="multimodal_ocr_yolo_conflict.dxf",
        version_no="V1",
        expected_rule_codes=["OCR_PLACEHOLDER_TEXT", "YOLO_TITLE_BLOCK_CAD_MISSING"],
        builder=build_missing_title,
        min_entity_count=5,
        required_layers=["S-HULL", "DIM-MAIN", "TEXT-NOTE"],
        required_blocks=[],
        description="Missing CAD title block plus mock title-block detection and OCR placeholder evidence.",
        required_entity_types=["DIMENSION"],
        expected_evidence={
            "OCR_PLACEHOLDER_TEXT": {
                "requireEntityRef": False,
                "requireEvidenceTypes": ["OCR_TEXT"],
                "locationCoordinateSpace": "RASTER_IMAGE",
            },
            "YOLO_TITLE_BLOCK_CAD_MISSING": {
                "requireEntityRef": False,
                "requireEvidenceTypes": ["YOLO_SYMBOL"],
                "locationCoordinateSpace": "RASTER_IMAGE",
            },
        },
        review_task={"autoVision": True, "autoOcr": True, "visionConfidence": 0.25, "ocrConfidence": 0.5},
        mock_vision={
            "detections": [
                {
                    "classId": 1,
                    "className": "title_block",
                    "confidence": 0.93,
                    "xyxy": [120.0, 80.0, 460.0, 220.0],
                }
            ],
            "imageWidth": 640,
            "imageHeight": 480,
            "engine": "mock-yolov8",
        },
        mock_ocr={
            "regions": [
                {
                    "text": "TBD bracket detail",
                    "confidence": 0.91,
                    "xyxy": [24.0, 32.0, 260.0, 68.0],
                    "language": "eng",
                }
            ],
            "imageWidth": 640,
            "imageHeight": 480,
            "engine": "mock-ocr",
            "language": "eng",
        },
    ),
]


def build_manifest() -> list[dict[str, object]]:
    manifest = []
    for case in CASES:
        item = {
                "id": case.case_id,
                "file": f"cases/{case.file_name}",
                "format": "dxf",
                "drawingNo": f"GOLDEN-{case.case_id.upper().replace('-', '_')}",
                "title": case.case_id.replace("_", " ").title(),
                "versionNo": case.version_no,
                "expectedRuleCodes": case.expected_rule_codes,
                "expectedIssueCount": len(case.expected_rule_codes),
                "allowUnexpectedRuleCodes": False,
                "parserExpectations": {
                    "minEntityCount": case.min_entity_count,
                    "requiredLayers": case.required_layers,
                    "requiredBlocks": case.required_blocks,
                    "requiredEntityTypes": case.required_entity_types,
                },
                "description": case.description,
            }
        if case.expected_evidence:
            item["expectedEvidence"] = case.expected_evidence
        if case.review_task:
            item["reviewTask"] = case.review_task
        if case.mock_vision:
            item["mockVision"] = case.mock_vision
        if case.mock_ocr:
            item["mockOcr"] = case.mock_ocr
        manifest.append(item)
    return manifest


def main() -> None:
    CASE_DIR.mkdir(parents=True, exist_ok=True)
    for case in CASES:
        case.builder(CASE_DIR / case.file_name)
    MANIFEST_PATH.write_text(json.dumps(build_manifest(), indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(f"Generated {len(CASES)} golden cases in {DATASET_DIR}")


if __name__ == "__main__":
    main()
