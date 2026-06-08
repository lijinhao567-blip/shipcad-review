from __future__ import annotations

from pathlib import Path

from tools.check_deployment_security import check_repository


def write_fixture(root: Path, compose: str, password: str = "") -> None:
    files = {
        "deploy/docker-compose.yml": compose,
        "backend-spring/src/main/resources/application.yml": "shipcad:\n  security:\n    seed-dev-users: false\n",
        "frontend-vue/src/App.vue": (
            "const loginState = reactive({ username: '', "
            f"password: '{password}', label: 'not logged in' }})\n"
        ),
    }
    for relative_path, content in files.items():
        path = root / relative_path
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content, encoding="utf-8")


def test_safe_defaults_pass(tmp_path: Path) -> None:
    write_fixture(
        tmp_path,
        'SPRING_PROFILES_ACTIVE: "${SPRING_PROFILES_ACTIVE:-}"\n'
        'MINIO_ROOT_PASSWORD: "${MINIO_ROOT_PASSWORD:-}"\n',
    )

    assert check_repository(tmp_path) == []


def test_fixed_passwords_are_rejected(tmp_path: Path) -> None:
    write_fixture(
        tmp_path,
        "SPRING_PROFILES_ACTIVE: dev\n"
        "MINIO_ROOT_PASSWORD: ${MINIO_ROOT_PASSWORD:-shipcadadmin123}\n"
        "SHIPCAD_S3_SECRET_KEY: ${SHIPCAD_S3_SECRET_KEY:-shipcadadmin123}\n",
        password="admin123",
    )

    failures = check_repository(tmp_path)

    assert len(failures) == 4
