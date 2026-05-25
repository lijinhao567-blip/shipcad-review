# Roadmap

This roadmap describes the open-source development path after the AGPL-3.0 MVP baseline.

## v0.1: Architecture Baseline

Status: current baseline.

- Vue 3 web workspace.
- Spring Boot backend with authentication, projects, drawings, versions, review tasks, issues, reports, and OpenAPI.
- Python CAD Worker for DXF parsing.
- Optional LibreDWG DWG conversion path.
- Optional YOLOv8 Vision Worker boundary.
- Easy Rules deterministic review engine.
- Docker Compose and Kubernetes skeleton.

## v0.1.1: Open-Source Integration Plan

Status: next.

- Use `docs/integration_strategy.md` as the rule for integrating external tools.
- Use `docs/evidence_model.md` as the target model for parser, vision, OCR, and knowledge graph outputs.
- Run the CAD viewer experiment in `docs/experiment_cad_viewer_integration.md`.
- Keep open-source tools behind adapters or workers instead of scattering direct calls through the codebase.

## v0.2: CAD Parsing Hardening

- Promote `dxf-viewer` as the official DXF preview path; keep Canvas as a manual diagnostic view, not an automatic fallback.
- Keep `mlightcad/cad-viewer` as a longer-term DWG/DXF research candidate until worker rendering issues are resolved.
- Improve DXF entity coverage: dimensions, hatches, leaders, attributes, nested blocks.
- Add public DWG compatibility samples where licensing permits.
- Add parser golden dataset and regression tests.
- Improve error reporting for malformed CAD files.

## v0.3: Rule Engine And Review Workflow

- Add configurable rule metadata.
- Add rule severity, category, and enable/disable control.
- Add rule result evidence model.
- Expand review workflow with assignment, comments, attachments, and review history.

## v0.4: YOLOv8 Symbol Recognition

- Validate CVAT-based dataset labeling flow.
- Define symbol taxonomy.
- Add dataset format and labeling guide.
- Add training and evaluation scripts.
- Store symbol detections as review evidence.
- Connect visual detections to issue generation.

## v0.5: Knowledge Graph And AI Assistance

- Define rule-to-standard mapping.
- Build domain concept schema for drawings, symbols, issues, and standards.
- Add AI-generated explanation and report summaries with traceable evidence.
- Keep deterministic rules as the source of truth for critical findings.

## v1.0: Pilot-Ready Release

- DM8 or PostgreSQL deployment profile.
- Redis-backed distributed task queue.
- MinIO object storage.
- Role-based permission hardening.
- Exportable review reports.
- Installation guide, operator guide, and sample dataset.
