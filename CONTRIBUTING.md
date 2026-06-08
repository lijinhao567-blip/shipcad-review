# Contributing

Thanks for helping improve ShipCAD Review.

## Development

1. Start CAD Worker, backend, and frontend locally.
2. Keep generated data out of Git.
3. Add tests or sample files when changing parser or rule behavior.
4. Update `THIRD_PARTY_LICENSES.md` when adding dependencies.
5. Pin direct Python Worker dependencies with `==` and run `python tools/check_python_requirements.py`.

## Commit Scope

Prefer small changes:

- parser changes
- rule engine changes
- frontend workflow changes
- documentation changes
- deployment changes

## Data Policy

Do not submit real ship drawings, customer files, credentials, logs, databases, model weights, or private datasets.

## Supply Chain Policy

Dependency changes should be small and reviewable. Do not auto-merge dependency
updates until Worker tests, backend tests, frontend build, and relevant E2E
checks pass. GitHub SBOM artifacts are generated for traceability after the
repository is published.
