# CAD Worker

CAD Worker parses CAD files, renders drawing versions to PNG, and returns normalized drawing entities for the backend.

## DXF

DXF is parsed directly with `ezdxf`.

Current normalized evidence includes layers, text entities, block references, block attributes, dimensions, basic geometry, entity counts, layer counts, empty layers, title-block names, drawing bounds, CAD handles, and per-entity geometry bounds.

## Rendering

`POST /render` accepts DXF/DWG files and returns a PNG rendered with `ezdxf.addons.drawing` and `matplotlib`.

The response includes an `X-ShipCAD-Render-Metadata` base64url JSON header. Its `modelBounds` value is the exact matplotlib axes viewport represented by the PNG, including aspect-ratio padding. The backend stores the PNG and a `render.metadata.json` sidecar, then reuses both for YOLO/OCR evidence generation and pixel-to-CAD coordinate mapping. Missing or invalid coordinate metadata is a render failure, not a silent fallback.

Rendering is an explicit CAD Worker capability, not a replacement for the official `dxf-viewer` preview path.

## DWG

DWG parsing and rendering use GNU LibreDWG command-line tools:

```text
DWG -> dwg2dxf -> temporary DXF -> ezdxf parser -> normalized entities
```

Runtime requirements:

- Install LibreDWG tools on the host or container image.
- Make sure `dwg2dxf` is available in PATH.
- Or set `LIBREDWG_DWG2DXF_BIN` to the executable path.

If LibreDWG is missing or conversion fails, the backend review task will enter `FAILED` and expose the error message.
