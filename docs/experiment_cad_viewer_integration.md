# Experiment: CAD Viewer Integration

Goal: evaluate whether an open-source CAD viewer can become the official DXF preview path while keeping the lightweight Canvas renderer as a manual diagnostic view.

## Candidate Tools

Primary candidate:

- `vagran/dxf-viewer`

Retired candidate:

- `mlightcad/cad-viewer`

Fallback reference:

- `three-dxf`

## Why This Matters

The current Canvas preview is useful for MVP-level positioning, but a mature viewer can provide:

- better zoom and pan
- layer control
- richer entity rendering
- faster large drawing handling
- future DWG/DXF viewing capability
- better issue highlight overlays

## Experiment Scope

The first experiment should not rewrite the whole frontend.

Build a small isolated proof of concept that checks:

1. Can it load `samples/dxf/valid_ship_section.dxf`?
2. Can it show layers?
3. Can it zoom and pan smoothly?
4. Can it expose entity or layer metadata?
5. Can we highlight an issue by layer, entity id, or bounding box?
6. Can it be embedded in the Vue app without breaking layout?

## Decision Criteria

Adopt as main viewer if:

- loading and rendering are stable
- issue highlighting is possible
- integration complexity is acceptable
- license is compatible and recorded

Keep Canvas available as a diagnostic tool if:

- highlighting cannot be controlled
- API is unstable
- viewer is too heavy for MVP
- DWG/DXF support does not match our needs

Canvas must not be used as an automatic fallback for the official preview path. If `dxf-viewer` fails, the UI should expose that failure so the primary viewer, file service, or DXF compatibility issue is fixed deliberately.

## Expected Output

- A short proof-of-concept report.
- Screenshots or notes about rendering quality.
- Recommendation: adopt, keep as optional, or reject.

## Current POC Result

POC directory:

```text
experiments/cad-viewer-poc
```

### mlightcad/cad-viewer

Result: retired from the project dependency tree.

Observed:

- npm version tested: `@mlightcad/cad-viewer@1.5.0`.
- Build succeeds in isolated Vue/Vite project.
- Component mounts and accepts `localFile`.
- Toolbar and UI render correctly.
- Project sample `valid_ship_section.dxf` is accepted.
- Drawing area remains blank.
- Browser reports worker errors.
- Build warns that package internals externalize Node `fs` for browser compatibility.
- Bundle size is large.
- Version 1.5.0 requires the exact peer `lodash-es@4.17.21`, which is affected by published prototype-pollution and code-injection advisories.
- The latest checked release, 1.5.5, keeps the same vulnerable exact peer constraint.

Decision:

- Remove it from the POC and runtime dependency tree.
- Do not reintroduce it through npm overrides that leave an invalid peer tree.
- Re-evaluate only if upstream releases a compatible version without the vulnerable lodash constraint and the rendering issues are independently resolved.

### vagran/dxf-viewer

Result: DXF rendering works for the project sample.

Observed:

- npm version tested: `dxf-viewer@1.0.47`.
- License: MPL-2.0.
- Build succeeds in isolated Vue/Vite project.
- `samples/dxf/valid_ship_section.dxf` renders in browser.
- Viewer reports 6 layers.
- Viewer exposes drawing bounds.
- API supports `GetLayers`, `GetBounds`, `ShowLayer`, and `FitView`.

Limitations:

- DXF only; no direct DWG support.
- UI is not a full CAD workstation UI.
- Issue highlighting by entity still needs a custom overlay or mapping strategy.
- The historical comparison bundle was larger when both viewers were installed in the same POC.

Decision:

- Use `dxf-viewer` as the official near-term DXF preview path.
- Keep the current `DxfCanvas` component as a manually opened diagnostic view for parsed-entity comparison only.
- Keep mlightcad out of the dependency tree unless upstream fixes both its exact vulnerable lodash peer and the observed rendering problems.

## Implemented Engineering Step

Create a main-frontend integration branch or small feature:

```text
frontend-vue/src/components/DxfViewerPreview.vue
```

Implemented path:

1. Added `dxf-viewer` to the main frontend only.
2. Load uploaded DXF preview through an authenticated backend file endpoint and a browser blob URL.
3. Display layers in side panel.
4. Keep current `DxfCanvas` as a manual diagnostics panel, not as automatic fallback.
5. Continue issue positioning through the structured evidence coordinate contract.

## Complex Fixture Regression

Issue #14 adds a dedicated parser and preview compatibility baseline under `datasets/parser/`.

- `complex_ship_section.dxf`: synthetic section drawing with hull contour, frames, stiffeners, HATCH regions, title attributes, dimensions, and weld-symbol blocks.
- `dense_deck_grid.dxf`: synthetic dense deck plan with repeated grid members, equipment outlines, HATCH panels, dimensions, notes, and repeated symbol blocks.

Automated acceptance:

```powershell
.\.venv\Scripts\python.exe tools\check_complex_dxf_dataset.py
node tools\check_dxf_viewer_dataset.mjs
```

The Python check validates CAD Worker parser summaries, HATCH bounds, render metadata, and nonblank PNG output. The Node check validates that `dxf-viewer`'s own parser accepts the same DXF files and sees the expected layers, blocks, and entity types. This is not a Canvas fallback and does not claim final WebGL visual quality; real browser smoke screenshots remain a release/demo verification step.

Browser smoke acceptance:

- Open the Vue frontend against a running backend and CAD Worker.
- Upload or select one `datasets/parser/cases/*.dxf` version through the authenticated product flow.
- Confirm `DxfViewerPreview` reports `dxf-viewer` load success, displays the expected layer list, and does not emit the official preview failure state.
- Capture the `.dxf-webgl-host` area and verify the image is not blank. This smoke checks the official WebGL preview path only; Canvas diagnostics must remain closed unless a human intentionally opens them.

Automated command:

```powershell
.\deploy\run-dxf-viewer-smoke.ps1
```

The script drives the authenticated product path from backend upload to frontend WebGL rendering and writes `.run/dxf-viewer-webgl-smoke.json` plus a WebGL canvas PNG at `.run/dxf-viewer-webgl-smoke.png`. It appends `dxf-preview-smoke=1` to the frontend URL only to preserve the WebGL drawing buffer for automated PNG verification; normal user preview keeps the default buffer behavior.

Local result on 2026-06-08:

- Fixture: `dense_deck_grid.dxf`.
- Product flow: login as the development administrator, create project/drawing/version through REST API, parse through CAD Worker, then select the uploaded version in the Vue preview panel.
- `dxf-viewer` result: loaded successfully with 13 layers and bounds approximately `-24,-45 - 384,184`.
- Screenshot smoke: `.dxf-webgl-host` crop was `502 x 519`, with 87,432 non-white pixels out of 260,538 pixels (`0.3356` non-white ratio).
- Failure state: no `viewer-error` or automatic Canvas fallback observed.
