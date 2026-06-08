# Third-Party Licenses

This project is licensed under GNU AGPL-3.0. Third-party dependencies keep their own licenses.

## Core Runtime

| Component | Purpose | License Notes |
|---|---|---|
| Spring Boot | Backend framework | Apache-2.0 |
| Spring Data JPA | Persistence layer | Apache-2.0 |
| Spring Data Redis | Redis protocol queue integration | Apache-2.0 |
| Lettuce | Redis client used by Spring Data Redis | MIT |
| AWS SDK for Java v2 S3 | S3-compatible object storage adapter | Apache-2.0 |
| Flyway Community | H2 schema migration | Apache-2.0 |
| springdoc-openapi | OpenAPI UI | Apache-2.0 |
| Easy Rules | Rule engine | MIT |
| H2 Database | Development database | MPL-2.0 / EPL-1.0 |
| Dameng DmJdbcDriver8 | Optional DM8 production JDBC driver | Apache-2.0 |
| Dameng Hibernate 6.6 Dialect | Optional DM8 production Hibernate dialect | Apache-2.0 |
| Vue | Frontend framework | MIT |
| Vite | Frontend build tool | MIT |
| ezdxf | DXF parsing | MIT |
| FastAPI | Python API framework | MIT |
| Uvicorn | ASGI server | BSD-3-Clause |
| dxf-viewer | Main DXF WebGL preview viewer | MPL-2.0 |
| Matplotlib | CAD Worker DXF-to-PNG rendering | Matplotlib License / PSF-style |
| Valkey | Redis protocol task queue service in deployment skeletons | BSD-3-Clause |
| redis-windows-fork | Development-only Windows Redis-compatible server used by `deploy/run-redis-queue-e2e.ps1` | MIT |
| MinIO | Optional S3-compatible object storage service for self-hosted deployments | AGPL-3.0 or commercial license |

## Development And CI Services

| Component | Purpose | License Notes |
|---|---|---|
| GitHub Dependabot | Hosted dependency update service used after repository publication | GitHub service; not part of the distributed runtime |
| GitHub Dependency Review Action | Pull-request dependency vulnerability review | MIT; uses GitHub dependency graph services |
| GitHub CodeQL Action | Static security analysis workflow | MIT |
| GitHub CodeQL CLI and query packs | Analysis engine used by the CodeQL workflow | GitHub CodeQL Terms and Conditions; CI-only, not distributed with the product |
| Anchore SBOM Action | CI generation of SPDX source SBOM artifacts | Apache-2.0; wraps Syft in GitHub Actions |
| Syft | SBOM generation engine used by the Anchore action | Apache-2.0 |
| Gitleaks CLI | Local and CI secret scanning for Git history | MIT; downloaded from official releases and verified by SHA-256 |
| GitHub CLI | Development-only repository publication and administration client | MIT; local tool is downloaded to `.tools` and not distributed with the product |

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
