# Third-Party Licenses

This project is licensed under GNU AGPL-3.0. Third-party dependencies keep their own licenses.

## Core Runtime

| Component | Purpose | License Notes |
|---|---|---|
| Spring Boot | Backend framework | Apache-2.0 |
| Spring Data JPA | Persistence layer | Apache-2.0 |
| springdoc-openapi | OpenAPI UI | Apache-2.0 |
| Easy Rules | Rule engine | MIT |
| H2 Database | Development database | MPL-2.0 / EPL-1.0 |
| Vue | Frontend framework | MIT |
| Vite | Frontend build tool | MIT |
| ezdxf | DXF parsing | MIT |
| FastAPI | Python API framework | MIT |
| Uvicorn | ASGI server | BSD-3-Clause |
| mlightcad/cad-viewer | Candidate DWG/DXF web viewer | MIT |
| dxf-viewer | Main DXF WebGL preview viewer | MPL-2.0 |
| Matplotlib | CAD Worker DXF-to-PNG rendering | Matplotlib License / PSF-style |

## CAD And AI

| Component | Purpose | License Notes |
|---|---|---|
| GNU LibreDWG | Optional DWG to DXF conversion | GPLv3+ |
| Ultralytics YOLOv8 | Optional symbol detection worker | AGPL-3.0 or Enterprise License |
| Pillow | Image loading in vision and OCR workers | HPND |
| Tesseract OCR | Optional OCR worker engine | Apache-2.0 |
| CVAT | Candidate dataset annotation platform | MIT |
| Label Studio | Candidate dataset annotation platform | Apache-2.0 |
| PaddleOCR | Candidate OCR engine | Apache-2.0 |
| OpenCV | Candidate image processing toolkit | Apache-2.0 |
| Apache Jena | Candidate knowledge graph framework | Apache-2.0 |
| Eclipse RDF4J | Candidate RDF framework | EDL-1.0 / BSD-style ecosystem |
| Drools | Candidate advanced rule engine | Apache-2.0 |

## Repository Policy

- Do not commit proprietary CAD drawings.
- Do not commit trained model weights such as `.pt`, `.onnx`, or TensorRT engine files.
- Do not commit locally installed SDKs, system binaries, virtual environments, or generated databases.
- If a dependency is added, update this file and mention whether it is required, optional, or development-only.
