# Open-Source Integration Strategy

ShipCAD Review should not reimplement mature CAD, AI, OCR, annotation, or graph infrastructure from scratch. The project value is in integrating strong open-source tools into a traceable ship drawing review workflow.

## Positioning

ShipCAD Review is an open-source integration platform for ship CAD review.

It should provide:

- Workflow: project, drawing, version, task, issue, remediation, report.
- Evidence model: CAD entities, visual detections, OCR text, standard clauses.
- Domain rules: ship drawing review logic.
- Tool adapters: stable boundaries around open-source components.
- Deployment: self-hosted and reproducible.

It should avoid becoming:

- A new CAD kernel.
- A new object detection framework.
- A new OCR engine.
- A general-purpose annotation platform.
- A general-purpose graph database.

## Integration Principle

External tools should enter the system through adapters or worker services, not through scattered direct calls.

Recommended boundaries:

```text
CADParserAdapter
  - EzdxfParser
  - LibreDwgParser

DrawingViewerAdapter
  - BuiltInCanvasViewer
  - MLightCadViewer
  - DxfViewer

VisionDetectorAdapter
  - YoloV8Detector

OcrAdapter
  - PaddleOcrAdapter
  - TesseractAdapter

KnowledgeGraphAdapter
  - JenaAdapter
  - Rdf4jAdapter

RuleEngineAdapter
  - EasyRulesAdapter
  - DroolsAdapter
```

The current codebase does not need all of these interfaces immediately. The important rule is that new integrations should preserve these boundaries.

## Fork Policy

Use upstream projects directly when possible.

Fork only when:

- The upstream project cannot expose the extension point we need.
- The change is strongly tied to drawing review interaction.
- We are willing to maintain the fork.

Prefer upstream pull requests for generic fixes.

Keep ship-review-specific behavior in this repository:

- issue highlighting
- review evidence overlays
- rule-to-standard mapping
- report generation
- ship drawing workflow

## License Policy

The project is AGPL-3.0. This allows integration with GPL/AGPL components such as LibreDWG and YOLOv8, but every new dependency still needs review.

When adding a dependency:

1. Record it in `THIRD_PARTY_LICENSES.md`.
2. Mark it as required, optional, or development-only.
3. Avoid committing proprietary data, model weights, generated databases, or local SDK binaries.

## Priority Order

The next integration work should follow this order:

1. CAD viewer integration: improve user-visible drawing preview and issue positioning.
2. Evidence model: make all parser, vision, OCR, and knowledge graph outputs traceable.
3. CVAT + YOLOv8 pipeline: build the first symbol detection workflow.
4. OCR pipeline: extract title block, revision, dimension, and note text.
5. Knowledge graph: connect rules and issues to standard clauses.
6. Rule engine migration: move from Easy Rules to Drools only when rule complexity justifies it.

## Success Criteria

An integration is successful only if it improves the review workflow, not merely because the tool runs.

For each integration, answer:

- What user problem does it solve?
- What evidence does it produce?
- How is the evidence stored?
- Can a review issue cite that evidence?
- Can the frontend display it?
- Can it fail safely?
- Is the license recorded?
