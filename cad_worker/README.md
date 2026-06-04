# CAD Worker

CAD Worker parses CAD files, renders drawing versions to PNG, and returns normalized drawing entities for the backend.

## DXF

DXF is parsed directly with `ezdxf`.

Current normalized evidence includes layers, text entities, block references, block attributes, dimensions, basic geometry, entity counts, layer counts, empty layers, title-block names, and drawing bounds.

## Rendering

`POST /render` accepts DXF/DWG files and returns a PNG rendered with `ezdxf.addons.drawing` and `matplotlib`.

The backend caches rendered version images under `data/rendered/{versionId}/render.png` and reuses them for YOLO/OCR evidence generation. Rendering is an explicit CAD Worker capability, not a replacement for the official `dxf-viewer` preview path.

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
