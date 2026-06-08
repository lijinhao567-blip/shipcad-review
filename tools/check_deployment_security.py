from __future__ import annotations

import argparse
import re
from pathlib import Path


def check_repository(repo: Path) -> list[str]:
    failures: list[str] = []
    compose = (repo / "deploy/docker-compose.yml").read_text(encoding="utf-8")
    app_config = (repo / "backend-spring/src/main/resources/application.yml").read_text(encoding="utf-8")
    frontend = (repo / "frontend-vue/src/App.vue").read_text(encoding="utf-8")

    forbidden_compose_fragments = {
        "SPRING_PROFILES_ACTIVE: dev": "Docker Compose must not enable the development profile by default",
        "MINIO_ROOT_PASSWORD: ${MINIO_ROOT_PASSWORD:-shipcadadmin123}": "MinIO must not have a fixed default password",
        "SHIPCAD_S3_SECRET_KEY: ${SHIPCAD_S3_SECRET_KEY:-shipcadadmin123}": "S3 must not have a fixed default secret",
    }
    for fragment, message in forbidden_compose_fragments.items():
        if fragment in compose:
            failures.append(message)

    if "seed-dev-users: false" not in app_config:
        failures.append("The base application profile must disable development user seeding")

    login_match = re.search(
        r"const loginState = reactive\(\{\s*username:\s*'([^']*)',\s*password:\s*'([^']*)'",
        frontend,
    )
    if login_match is None:
        failures.append("Could not verify the frontend login defaults")
    elif login_match.group(2):
        failures.append("The frontend must not prefill a login password")

    return failures


def main() -> int:
    parser = argparse.ArgumentParser(description="Check public deployment security defaults.")
    parser.add_argument("--repo", type=Path, default=Path.cwd())
    args = parser.parse_args()

    failures = check_repository(args.repo.resolve())
    if failures:
        for failure in failures:
            print(f"[FAIL] {failure}")
        return 1

    print("Deployment security defaults are safe for public distribution.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
