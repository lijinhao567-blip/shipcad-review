# LibreDWG Setup

DWG parsing is optional and depends on GNU LibreDWG command-line tools.

## How It Works

```text
DWG upload -> cad_worker -> dwg2dxf -> temporary DXF -> ezdxf parser -> backend review task
```

The backend and frontend do not parse DWG directly. They call CAD Worker through the same `/parse` interface used for DXF.

## Runtime Requirement

CAD Worker expects a `dwg2dxf` executable.

Options:

- Put `dwg2dxf` in PATH.
- Or set `LIBREDWG_DWG2DXF_BIN` to the executable path.

Example:

```powershell
$env:LIBREDWG_DWG2DXF_BIN="D:\tools\libredwg\bin\dwg2dxf.exe"
.\.venv\Scripts\python.exe -m uvicorn cad_worker.app.main:app --host 127.0.0.1 --port 9000
```

## Expected Behavior

- If LibreDWG is installed and conversion succeeds, DWG files follow the same parser and rule pipeline as DXF.
- If LibreDWG is missing, the review task fails with a clear error message.
- If a DWG version is unsupported by LibreDWG, the task fails and can be retried after conversion tooling is updated.

## Testing

The current test suite includes the missing-tool error path. A real DWG compatibility suite should be added only with public or synthetic samples.
