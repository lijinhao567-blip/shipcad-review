# External DXF Candidates

This directory stores metadata only. Third-party DXF files are not committed to the repository.

`manifest.json` pins every candidate to a specific source commit and records:

- source repository, path, and immutable download URL
- license and attribution
- SHA-256 and expected file size
- CAD Worker parser/render expectations
- `dxf-viewer` parser expectations

Validation downloads files into the ignored `.run/external-dxf-candidates/` directory:

```powershell
.\.venv\Scripts\python.exe tools\check_external_dxf_candidates.py
node tools\check_external_dxf_viewer_candidates.mjs
```

To validate only the tracked manifest without network access:

```powershell
.\.venv\Scripts\python.exe tools\check_external_dxf_candidates.py --manifest-only
```

These samples are compatibility and regression candidates. They are not rule-compliance ground truth and must not be used to claim classification-society compliance.
