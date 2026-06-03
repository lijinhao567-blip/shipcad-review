# Dataset Specification

This document defines the dataset structure for parser regression, rule validation, and YOLOv8 symbol detection.

## Repository Policy

Allowed:

- Self-created DXF/DWG samples.
- Public samples with compatible licenses.
- Small synthetic images for documentation and tests.
- Label files without proprietary geometry if they are generated from synthetic data.

Not allowed:

- Real shipyard or customer CAD drawings.
- Confidential design data.
- Private model weights.
- Large training outputs.

## Parser And Rule Dataset

Use this structure:

```text
datasets/
  parser/
    cases/
      valid_title_block.dxf
      missing_title_block.dxf
    manifest.json
  rules/
    cases/
      layer_name_invalid.dxf
      placeholder_text.dxf
    expected.json
```

`manifest.json` should describe parser expectations:

```json
[
  {
    "file": "valid_title_block.dxf",
    "format": "dxf",
    "parserExpectations": {
      "minEntityCount": 1,
      "requiredLayers": ["S-STRUCTURE", "S-TITLE"],
      "requiredBlocks": ["TITLE_BLOCK"],
      "requiredEntityTypes": ["INSERT", "ATTRIB", "DIMENSION"]
    }
  }
]
```

`expected.json` should describe rule expectations:

```json
[
  {
    "file": "placeholder_text.dxf",
    "expectedIssues": [
      {
        "ruleCode": "TEXT_PLACEHOLDER",
        "severity": "MEDIUM"
      }
    ],
    "expectedEvidence": {
      "TEXT_PLACEHOLDER": {
        "layerName": "TEXT-NOTE",
        "requireEntityRef": true
      }
    }
  }
]
```

The system should not identify samples by file name. File names only help tests locate input files. The actual result must come from CAD parsing and rule execution.

For `datasets/rules/expected.json`, parser expectations use these machine-checkable keys:

- `minEntityCount`: minimum parsed entity count after expanding supported nested evidence such as block attributes.
- `requiredLayers`: layer names that must appear in the parsed DXF layer table.
- `requiredBlocks`: block names that must appear in parsed block references or related attributes.
- `requiredEntityTypes`: entity types such as `DIMENSION` or `ATTRIB` that must appear in the parsed summary.

Current rule golden dataset:

```text
datasets/rules/cases/*.dxf
datasets/rules/expected.json
```

Generation and acceptance commands:

```powershell
.\.venv\Scripts\python.exe tools\generate_golden_dataset.py
.\.venv\Scripts\python.exe tools\run_golden_e2e.py --keep-going
```

The E2E script uploads each DXF, creates a review task, polls until it finishes, checks the generated `ReviewIssue.ruleCode` set, validates parser summary expectations, verifies issue evidence fields such as `layerName` and `entityRef`, and checks the authenticated version file endpoint used by the official preview path.

Current deterministic rule coverage:

- `LAYER_NAME_STANDARD`: nonstandard layer names.
- `EMPTY_LAYER_CHECK`: declared custom layers without entities.
- `TITLE_BLOCK_REQUIRED`: missing title block evidence.
- `VERSION_FORMAT`: non-traceable uploaded version numbers.
- `TEXT_PLACEHOLDER`: unfinished placeholder text such as `TBD`.
- `ENTITY_DENSITY`: abnormally sparse drawings.
- `TITLE_ATTRIBUTE_REQUIRED`: missing title block attributes such as `DRAWING_NO` or `REVISION`.
- `VERSION_TITLE_CONSISTENCY`: title block revision inconsistent with uploaded version number.
- `DIMENSION_REQUIRED`: structured drawings without parsed `DIMENSION` entities.
- `DIMENSION_LAYER_STANDARD`: dimensions placed outside `DIM-*` layers.

## YOLOv8 Dataset

Use YOLO detection format:

```text
datasets/
  vision/
    images/
      train/
      val/
    labels/
      train/
      val/
    data.yaml
```

Label format per image:

```text
class_id x_center y_center width height
```

Coordinates are normalized to image width and height.

Recommended first symbol classes are defined in `docs/yolov8_symbol_taxonomy.md`.
