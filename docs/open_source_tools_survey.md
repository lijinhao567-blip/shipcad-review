# Open Source Tools Survey

This survey records open-source tools that can support ShipCAD Review. It focuses on CAD parsing, CAD viewing, dataset labeling, OCR, AI vision, knowledge graph, and rules.

## Selection Principles

- Prefer tools that can be self-hosted.
- Prefer tools with clear open-source licenses.
- Keep CAD, AI, OCR, and knowledge graph capabilities behind service boundaries.
- Do not commit proprietary drawings, trained model weights, private datasets, or commercial SDKs.

## Recommended Shortlist

| Priority | Tool | Area | License | Recommended Use |
|---:|---|---|---|---|
| P0 | ezdxf | DXF parsing | MIT | Continue as primary DXF parser in CAD Worker. |
| P0 | GNU LibreDWG | DWG conversion/parsing | GPLv3+ | Continue DWG path through `dwg2dxf`, then parse with ezdxf. |
| P1 | mlightcad/cad-viewer | Web DWG/DXF viewer | MIT | Research replacing or enhancing current Canvas preview. |
| P1 | CVAT | YOLO dataset labeling | MIT | Use for symbol bounding-box annotation. |
| P1 | Ultralytics YOLOv8 | Symbol detection | AGPL-3.0 / Enterprise | Continue as Vision Worker inference and training framework. |
| P2 | PaddleOCR | OCR | Apache-2.0 | Add title block, revision table, and note recognition. |
| P2 | Apache Jena | Knowledge graph | Apache-2.0 | Prototype rule-standard-issue knowledge graph. |
| P2 | Drools | Rules | Apache-2.0 | Migrate after Easy Rules becomes insufficient. |

## CAD Parsing And Conversion

### ezdxf

Current status: already used.

Use cases:

- Parse DXF layers, entities, text, blocks, and geometry.
- Generate parser summaries.
- Potentially render DXF to SVG/PNG/PDF for YOLO dataset preparation.

Integration plan:

- Keep as the stable parser core.
- Expand supported entities: dimensions, hatches, leaders, attributes, nested blocks.
- Add golden dataset regression tests.

### GNU LibreDWG

Current status: optional DWG path already wired through `cad_worker/app/dwg_adapter.py`.

Use cases:

- Convert DWG to DXF using `dwg2dxf`.
- Inspect DWG layers or text through LibreDWG command-line tools.
- Potentially evaluate JSON output for richer metadata.

Integration plan:

- Keep `dwg2dxf -> ezdxf` as the first stable path.
- Add integration tests only with public or synthetic DWG samples.
- Document version compatibility limits clearly.

Risks:

- DWG compatibility varies by file version and feature set.
- Conversion errors must be visible in review task failure messages.

### mlightcad/libredwg-web

Use cases:

- Browser or Node.js DWG/DXF parsing based on LibreDWG.
- Research front-end-only parsing or client-side privacy-preserving preview.

Integration plan:

- Research only.
- Do not replace CAD Worker until performance, security, and licensing implications are clear.

## CAD Viewing

### mlightcad/cad-viewer

Use cases:

- High-performance browser DWG/DXF viewing.
- WebGL/Three.js rendering.
- Layer control, zoom, pan, measure, offline HTML export.

Integration plan:

1. Build an isolated proof of concept in Vue.
2. Test it with existing sample DXF files.
3. Check whether it exposes entity selection and layer data needed for issue highlighting.
4. If it works, replace the current lightweight Canvas preview or run both views side by side.

Risks:

- Need to confirm API stability.
- Need to ensure issue highlighting can be integrated cleanly.
- Current POC mounts the component but renders a blank drawing area with worker errors. Keep as research candidate.

### vagran/dxf-viewer

Use cases:

- DXF-only WebGL preview.
- Useful reference for layer rendering, batching, and Web Worker offloading.

Integration plan:

- Prefer as near-term DXF preview candidate because current POC renders `samples/dxf/valid_ship_section.dxf` and exposes layers/bounds.
- Use current Canvas preview as fallback during integration.

### three-dxf

Use cases:

- Simple educational reference for DXF-to-three.js rendering.

Integration plan:

- Use as learning/reference only unless a very lightweight viewer is needed.

## Dataset Labeling

### CVAT

Use cases:

- Bounding-box annotation for YOLOv8 symbol detection.
- Team labeling workflow.
- Export YOLO-format labels.

Integration plan:

1. Render CAD drawings into images.
2. Import images into CVAT.
3. Label classes from `docs/yolov8_symbol_taxonomy.md`.
4. Export YOLO dataset into the structure defined in `docs/dataset_spec.md`.

Recommended for:

- welding_symbol
- arrow
- section_marker
- title_block
- revision_block
- dimension_text

### Label Studio

Use cases:

- More general labeling, including image regions, OCR regions, text classification, and rule review samples.

Integration plan:

- Use if annotation goes beyond pure object detection.
- Good candidate for reviewing issue labels or matching text to standard clauses.

## AI Vision

### Ultralytics YOLOv8

Current status: Vision Worker skeleton exists.

Use cases:

- Detect visual symbols from rendered drawing images.
- Return bounding boxes, classes, and confidence.

Integration plan:

1. Prepare public/synthetic dataset.
2. Train a small initial model.
3. Put model file under local `models/`, not Git.
4. Configure `YOLO_MODEL_PATH`.
5. Store detections in backend as evidence.
6. Let deterministic rules or knowledge graph convert evidence into review issues.

Principle:

YOLO should detect evidence. It should not be the only source of final compliance judgment.

## OCR And Image Processing

### PaddleOCR

Use cases:

- Title block OCR.
- Revision table OCR.
- General note extraction.
- Chinese and English text recognition.

Integration plan:

- Add an OCR Worker or extend Vision Worker later.
- Start with title block and revision block crops from YOLO detections.

### Tesseract

Use cases:

- Offline OCR fallback.
- Good for simple English/numeric fields.

Integration plan:

- Use as fallback or baseline.
- Prefer PaddleOCR for mixed Chinese/English CAD text.

### OpenCV

Use cases:

- Preprocess rendered drawing images.
- Crop regions.
- Denoise, threshold, detect lines, find title block candidates.

Integration plan:

- Add to Vision Worker when preprocessing becomes necessary.

## Knowledge Graph

### Apache Jena

Use cases:

- RDF/SPARQL knowledge graph.
- Rule-standard-issue mapping.
- Traceable relation between drawing evidence and standards.

Integration plan:

1. Define small ontology:
   - DrawingObject
   - Symbol
   - ReviewRule
   - StandardClause
   - ReviewIssue
   - RemediationSuggestion
2. Store rule-to-clause mapping.
3. Query relevant clauses when generating explanations and reports.

### Eclipse RDF4J

Use cases:

- Alternative Java RDF framework.

Integration plan:

- Consider if Jena does not fit Spring Boot integration needs.

### Neo4j Community Edition

Use cases:

- Property graph exploration and visual graph queries.

Integration plan:

- Research only for now.
- RDF/SPARQL is more suitable if standards and clauses become primary knowledge assets.

## Rules And Workflow

### Easy Rules

Current status: already used.

Use cases:

- Lightweight deterministic checks.

Integration plan:

- Keep for current MVP.
- Add rule metadata and test coverage.

### Drools

Use cases:

- Larger rule library.
- Decision tables.
- Rule versioning and expert-managed rules.

Integration plan:

- Migrate only after rule count and complexity justify it.
- Keep `RuleEngine` interface stable so the implementation can be replaced.

## Recommended Next Experiments

### Experiment 1: CAD Viewer Integration

Goal: evaluate whether `mlightcad/cad-viewer` can replace the current Canvas preview.

Steps:

1. Create a small isolated Vue proof of concept.
2. Load `samples/dxf/valid_ship_section.dxf`.
3. Check zoom, pan, layer display, and entity selection.
4. Check whether issue highlighting can be implemented.

Decision:

- If entity/layer highlighting is possible, promote it to main preview candidate.
- If not, keep current Canvas preview and use cad-viewer only as a secondary full-view mode.

### Experiment 2: CVAT + YOLO Dataset Flow

Goal: validate the dataset pipeline before collecting many samples.

Steps:

1. Render 5-10 synthetic CAD drawings to images.
2. Label initial symbol classes in CVAT.
3. Export YOLO dataset.
4. Train a small YOLOv8 model.
5. Run through Vision Worker.

Decision:

- If symbol boxes are stable, expand dataset.
- If not, simplify class taxonomy.
