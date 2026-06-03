# CAD Worker

CAD Worker parses CAD files and returns normalized drawing entities for the backend.

## DXF

DXF is parsed directly with `ezdxf`.

Current normalized evidence includes layers, text entities, block references, block attributes, dimensions, basic geometry, entity counts, layer counts, empty layers, title-block names, and drawing bounds.

## DWG

DWG support uses GNU LibreDWG command-line tools:

```text
DWG -> dwg2dxf -> temporary DXF -> ezdxf parser -> normalized entities
```

Runtime requirements:

- Install LibreDWG tools on the host or container image.
- Make sure `dwg2dxf` is available in PATH.
- Or set `LIBREDWG_DWG2DXF_BIN` to the executable path.

If LibreDWG is missing or conversion fails, the backend review task will enter `FAILED` and expose the error message.
