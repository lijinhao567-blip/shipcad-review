# Experiment: CAD Viewer Integration

Goal: evaluate whether an open-source CAD viewer can become the official DXF preview path while keeping the lightweight Canvas renderer as a manual diagnostic view.

## Candidate Tools

Primary candidate:

- `mlightcad/cad-viewer`

Fallback candidates:

- `vagran/dxf-viewer`
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

Result: component integration works, but rendering is not yet reliable in this project environment.

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

Decision:

- Do not promote to main frontend yet.
- Keep as a research candidate.
- Revisit if worker configuration, bundling, or package version issues can be resolved.

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
- Bundle size increases when both mlightcad and dxf-viewer are installed in the same POC.

Decision:

- Use `dxf-viewer` as the official near-term DXF preview path.
- Keep the current `DxfCanvas` component as a manually opened diagnostic view for parsed-entity comparison only.
- Keep `mlightcad/cad-viewer` as a longer-term DWG/DXF viewer candidate, but do not integrate it into the main app until the rendering issue is understood.

## Next Engineering Step

Create a main-frontend integration branch or small feature:

```text
frontend-vue/src/components/DxfViewerPreview.vue
```

Recommended path:

1. Add `dxf-viewer` to the main frontend only.
2. Load uploaded DXF preview through an authenticated backend file endpoint and a browser blob URL.
3. Display layers in side panel.
4. Keep current `DxfCanvas` as a manual diagnostics panel, not as automatic fallback.
5. Implement issue highlighting first by layer visibility/color, then by bounding box overlay.
