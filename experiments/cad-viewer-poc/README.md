# CAD Viewer POC

This experiment validates the `dxf-viewer` integration used by ShipCAD Review.

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

## dxf-viewer

Status: preferred near-term DXF preview candidate.

Observed:

- Builds with Vue/Vite.
- Renders `samples/dxf/valid_ship_section.dxf`.
- Exposes layer names and drawing bounds.
- Provides API candidates for layer visibility and view fitting.

Limitations:

- DXF only.
- Issue highlighting still needs a custom overlay or mapping strategy.

## Retired mlightcad path

The earlier comparison also included `@mlightcad/cad-viewer`, but that path was
removed on June 8, 2026. The current mlightcad 1.5.x packages require the exact
peer `lodash-es@4.17.21`, which is affected by published prototype-pollution
and code-injection advisories. The latest 1.5.5 release keeps the same peer
constraint, so forcing a newer lodash version would leave an invalid dependency
tree. The main product had already selected `dxf-viewer`; keeping the unused
research dependency would only preserve avoidable security debt.

## Notes

This POC intentionally lives outside `frontend-vue` to avoid destabilizing the
main UI. The focused `DxfViewerPreview.vue` component is now implemented in the
main frontend, while Canvas remains a manual diagnostic view rather than an
automatic fallback.
