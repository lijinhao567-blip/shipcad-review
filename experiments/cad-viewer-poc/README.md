# CAD Viewer POC

This experiment evaluates open-source CAD viewers for ShipCAD Review.

## Run

```powershell
npm install --cache ..\..\.npm-cache
npm run dev
```

Open:

```text
http://127.0.0.1:5180
```

Production preview:

```powershell
npm run build
npm run preview
```

## Tested Viewers

### dxf-viewer

Status: preferred near-term DXF preview candidate.

Observed:

- Builds with Vue/Vite.
- Renders `samples/dxf/valid_ship_section.dxf`.
- Exposes layer names and drawing bounds.
- Provides API candidates for layer visibility and view fitting.

Limitations:

- DXF only.
- Issue highlighting still needs a custom overlay or mapping strategy.

### mlightcad/cad-viewer

Status: research candidate.

Observed:

- Builds with Vue/Vite in an isolated app when peer dependencies are pinned.
- Mounts successfully and accepts `localFile`.
- UI renders.
- Drawing area stayed blank in the current test.
- Browser logs worker errors.
- Bundle size is large.

## Notes

This POC intentionally lives outside `frontend-vue` to avoid destabilizing the main UI. The recommended next step is to add a focused `DxfViewerPreview.vue` component to the main frontend using `dxf-viewer`, while keeping the current Canvas preview as fallback.
