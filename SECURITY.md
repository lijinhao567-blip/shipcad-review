# Security Policy

## Supported Versions

The project is currently in the `0.x` development series. Security fixes are
applied to the latest commit on the default branch. Older commits, local forks,
and unofficial builds are not maintained as separate supported releases.

## Reporting A Vulnerability

After the repository is published, use GitHub private vulnerability reporting:

1. Open the repository's **Security** tab.
2. Select **Advisories** and **Report a vulnerability**.
3. Include affected versions, reproduction conditions, impact, and a minimal
   sanitized proof of concept.

If private vulnerability reporting is unavailable, contact the maintainer
through their GitHub profile to establish a private channel. Do not publish
exploit details in an issue.

We aim to acknowledge a report within three working days and provide an initial
triage result within seven working days. Resolution time depends on severity,
reproducibility, and the affected deployment boundary.

## Scope

Security-sensitive areas include:

- CAD file upload and parsing.
- Authentication and token handling.
- Project authorization and cross-project data isolation.
- Review task queues and object storage.
- Report generation.
- Worker service boundaries.
- Model and dataset handling.
- Deployment manifests and secret handling.

## Data Handling

Do not attach proprietary CAD drawings, credentials, production logs, database
files, access tokens, or trained model files to public issues. Replace customer
identifiers and drawing content with a minimal synthetic reproduction.

## Local Checks

Before publishing or importing history, run:

```powershell
.\.venv\Scripts\python.exe tools\run_secret_scan.py
.\.venv\Scripts\python.exe tools\check_action_pins.py
```

If a real secret is detected, rotate it first. Removing the text from a later
commit is not enough while the secret remains reachable in Git history.

## Disclosure

Please allow maintainers a reasonable remediation window before public
disclosure. Once a fix is available, the project will document affected
versions, impact, mitigation, and upgrade guidance without exposing customer
data.
