from __future__ import annotations

import argparse
import base64
import json
import sys
import threading
import time
from dataclasses import dataclass
from datetime import datetime, timezone
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from typing import Any

import httpx


ROOT = Path(__file__).resolve().parents[1]
DEFAULT_DXF = ROOT / "datasets" / "rules" / "cases" / "missing_title_block.dxf"
PNG_1X1 = base64.b64decode(
    "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/p9sAAAAASUVORK5CYII="
)
REQUIRED_RULES = {"OCR_PLACEHOLDER_TEXT", "YOLO_TITLE_BLOCK_CAD_MISSING"}


@dataclass
class MockWorker:
    name: str
    server: ThreadingHTTPServer
    thread: threading.Thread

    def start(self) -> None:
        self.thread.start()

    def stop(self) -> None:
        self.server.shutdown()
        self.server.server_close()
        self.thread.join(timeout=5)


class MockVisionHandler(BaseHTTPRequestHandler):
    def do_GET(self) -> None:
        if self.path.startswith("/health"):
            self.write_json({"status": "ok"})
            return
        if self.path.startswith("/capabilities"):
            self.write_json({"engine": "mock-yolov8", "modelConfigured": True})
            return
        self.send_error(404)

    def do_POST(self) -> None:
        self.consume_body()
        if not self.path.startswith("/detect"):
            self.send_error(404)
            return
        self.write_json(
            {
                "detections": [
                    {
                        "classId": 1,
                        "className": "title_block",
                        "confidence": 0.93,
                        "xyxy": [120.0, 80.0, 460.0, 220.0],
                    }
                ],
                "imageWidth": 640,
                "imageHeight": 480,
                "engine": "mock-yolov8",
            }
        )

    def log_message(self, format: str, *args: object) -> None:
        return

    def consume_body(self) -> None:
        length = int(self.headers.get("Content-Length") or 0)
        if length:
            self.rfile.read(length)

    def write_json(self, payload: dict[str, Any]) -> None:
        body = json.dumps(payload).encode("utf-8")
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)


class MockOcrHandler(BaseHTTPRequestHandler):
    def do_GET(self) -> None:
        if self.path.startswith("/health"):
            self.write_json({"status": "ok"})
            return
        if self.path.startswith("/capabilities"):
            self.write_json({"engine": "mock-ocr", "commandAvailable": True, "language": "eng"})
            return
        self.send_error(404)

    def do_POST(self) -> None:
        self.consume_body()
        if not self.path.startswith("/ocr"):
            self.send_error(404)
            return
        self.write_json(
            {
                "regions": [
                    {
                        "text": "TBD bracket detail",
                        "confidence": 0.91,
                        "xyxy": [24.0, 32.0, 260.0, 68.0],
                        "language": "eng",
                    }
                ],
                "imageWidth": 640,
                "imageHeight": 480,
                "engine": "mock-ocr",
                "language": "eng",
            }
        )

    def log_message(self, format: str, *args: object) -> None:
        return

    def consume_body(self) -> None:
        length = int(self.headers.get("Content-Length") or 0)
        if length:
            self.rfile.read(length)

    def write_json(self, payload: dict[str, Any]) -> None:
        body = json.dumps(payload).encode("utf-8")
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)


class MultimodalEvidenceE2E:
    def __init__(self, base_url: str, username: str, password: str, poll_seconds: int) -> None:
        self.base_url = base_url.rstrip("/")
        self.username = username
        self.password = password
        self.poll_seconds = poll_seconds
        self.client = httpx.Client(base_url=self.base_url, timeout=30.0)
        self.headers: dict[str, str] = {}

    def close(self) -> None:
        self.client.close()

    def request(self, method: str, path: str, **kwargs: Any) -> httpx.Response:
        headers = dict(self.headers)
        headers.update(kwargs.pop("headers", {}))
        response = self.client.request(method, path, headers=headers, **kwargs)
        if response.status_code >= 400:
            try:
                detail = response.json()
            except ValueError:
                detail = response.text
            raise RuntimeError(f"{method} {path} failed with {response.status_code}: {detail}")
        return response

    def login(self) -> None:
        response = self.request("POST", "/api/auth/login", json={"username": self.username, "password": self.password})
        self.headers = {"Authorization": f"Bearer {response.json()['token']}"}

    def assert_required_rules(self) -> None:
        rules = self.request("GET", "/api/rules").json()
        enabled = {rule.get("code") for rule in rules if rule.get("enabled")}
        missing = sorted(REQUIRED_RULES - enabled)
        if missing:
            raise AssertionError(
                f"backend is missing enabled rules {missing}; restart Spring Boot so DataInitializer can seed them"
            )

    def create_project(self) -> dict[str, Any]:
        stamp = datetime.now(timezone.utc).strftime("%Y%m%d%H%M%S")
        return self.request(
            "POST",
            "/api/projects",
            json={
                "name": f"Multimodal Evidence E2E {stamp}",
                "shipNo": f"EVIDENCE-{stamp}",
                "owner": "Automated Acceptance",
                "description": "Generated by tools/run_multimodal_evidence_e2e.py",
            },
        ).json()

    def create_drawing(self, project_id: str) -> dict[str, Any]:
        return self.request(
            "POST",
            "/api/drawings",
            json={
                "projectId": project_id,
                "drawingNo": "EVIDENCE-CROSS-CHECK-001",
                "title": "Evidence Cross Check Section",
                "discipline": "Hull Structure",
            },
        ).json()

    def upload_version(self, drawing_id: str, dxf_path: Path) -> dict[str, Any]:
        with dxf_path.open("rb") as file:
            response = self.request(
                "POST",
                "/api/versions/upload",
                data={"drawingId": drawing_id, "versionNo": "V1"},
                files={"file": (dxf_path.name, file, "application/dxf")},
            )
        return response.json()

    def run_vision_detection(self, version_id: str) -> list[dict[str, Any]]:
        return self.post_png(f"/api/versions/{version_id}/vision-detect?confidence=0.25", "mock-title-block.png").json()

    def run_ocr_recognition(self, version_id: str) -> list[dict[str, Any]]:
        return self.post_png(f"/api/versions/{version_id}/ocr-recognize?confidence=0.5", "mock-ocr-placeholder.png").json()

    def post_png(self, path: str, file_name: str) -> httpx.Response:
        return self.request(
            "POST",
            path,
            files={"file": (file_name, PNG_1X1, "image/png")},
        )

    def create_review_task(self, version_id: str) -> dict[str, Any]:
        return self.request("POST", "/api/review-tasks", json={"versionId": version_id}).json()

    def wait_for_task(self, task_id: str) -> dict[str, Any]:
        deadline = time.time() + self.poll_seconds
        last_task: dict[str, Any] | None = None
        while time.time() < deadline:
            task = self.request("GET", f"/api/review-tasks/{task_id}").json()
            last_task = task
            if task["status"] in {"FINISHED", "FAILED"}:
                return task
            time.sleep(1.0)
        raise TimeoutError(f"Review task {task_id} did not finish in {self.poll_seconds}s. Last task={last_task}")

    def issues(self, task_id: str) -> list[dict[str, Any]]:
        return self.request("GET", "/api/issues", params={"taskId": task_id}).json()

    def version_evidence(self, version_id: str, evidence_type: str) -> list[dict[str, Any]]:
        return self.request("GET", f"/api/versions/{version_id}/evidences", params={"type": evidence_type}).json()

    def create_report(self, task_id: str) -> dict[str, Any]:
        return self.request("POST", "/api/reports", json={"taskId": task_id}).json()

    def run(self, dxf_path: Path) -> dict[str, Any]:
        self.login()
        self.assert_required_rules()
        project = self.create_project()
        drawing = self.create_drawing(project["id"])
        version = self.upload_version(drawing["id"], dxf_path)

        yolo_version_evidence = self.run_vision_detection(version["id"])
        ocr_version_evidence = self.run_ocr_recognition(version["id"])
        if not yolo_version_evidence:
            raise AssertionError("vision-detect did not create YOLO_SYMBOL evidence")
        if not ocr_version_evidence:
            raise AssertionError("ocr-recognize did not create OCR_TEXT evidence")
        if not self.version_evidence(version["id"], "YOLO_SYMBOL"):
            raise AssertionError("version evidence endpoint did not return YOLO_SYMBOL evidence")
        if not self.version_evidence(version["id"], "OCR_TEXT"):
            raise AssertionError("version evidence endpoint did not return OCR_TEXT evidence")

        task = self.create_review_task(version["id"])
        finished = self.wait_for_task(task["id"])
        if finished["status"] != "FINISHED":
            raise AssertionError(f"review task failed: {finished.get('errorMessage')}")

        issues = self.issues(task["id"])
        self.assert_issue_chain(issues, "YOLO_TITLE_BLOCK_CAD_MISSING", "YOLO_SYMBOL", yolo_version_evidence[0]["id"])
        self.assert_issue_chain(issues, "OCR_PLACEHOLDER_TEXT", "OCR_TEXT", ocr_version_evidence[0]["id"])
        report = self.create_report(task["id"])
        self.assert_report(report)

        return {
            "projectId": project["id"],
            "drawingId": drawing["id"],
            "versionId": version["id"],
            "taskId": task["id"],
            "issueRules": sorted({issue["ruleCode"] for issue in issues}),
            "reportId": report["id"],
        }

    def assert_issue_chain(
        self,
        issues: list[dict[str, Any]],
        rule_code: str,
        expected_evidence_type: str,
        source_evidence_id: str,
    ) -> None:
        matches = [issue for issue in issues if issue.get("ruleCode") == rule_code]
        if not matches:
            actual = sorted({issue.get("ruleCode") for issue in issues})
            raise AssertionError(f"missing issue for {rule_code}; actual rules={actual}")
        issue = matches[0]
        evidence_items = issue.get("evidences") or []
        evidence_types = {item.get("evidenceType") for item in evidence_items}
        required = {"RULE_RESULT", "KNOWLEDGE_CLAUSE", expected_evidence_type}
        missing = sorted(required - evidence_types)
        if missing:
            raise AssertionError(f"{rule_code} missing evidence types {missing}; evidenceTypes={sorted(evidence_types)}")
        explanation = issue.get("aiExplanation") or {}
        if not explanation.get("summary") or not explanation.get("basis"):
            raise AssertionError(f"{rule_code} is missing AI explanation derived from evidence")
        source_refs = [
            item
            for item in evidence_items
            if item.get("evidenceType") == expected_evidence_type
            and source_evidence_id in (item.get("payloadJson") or "")
        ]
        if not source_refs:
            raise AssertionError(f"{rule_code} did not cite sourceEvidenceId={source_evidence_id}")

    def assert_report(self, report: dict[str, Any]) -> None:
        content = report.get("content") or ""
        for rule_code in REQUIRED_RULES:
            if rule_code not in content:
                raise AssertionError(f"report is missing rule {rule_code}")
        if "YOLO_SYMBOL" not in content or "OCR_TEXT" not in content:
            raise AssertionError("report is missing multimodal evidence types")
        if "AI" not in content:
            raise AssertionError("report is missing AI explanation section")


def make_mock_worker(name: str, host: str, port: int, handler: type[BaseHTTPRequestHandler]) -> MockWorker:
    server = ThreadingHTTPServer((host, port), handler)
    thread = threading.Thread(target=server.serve_forever, name=name, daemon=True)
    return MockWorker(name, server, thread)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description=(
            "Run an end-to-end check for YOLO/OCR version evidence being consumed by review rules. "
            "The backend and CAD worker must already be running."
        )
    )
    parser.add_argument("--base-url", default="http://127.0.0.1:8080", help="Spring Boot API base URL")
    parser.add_argument("--username", default="admin")
    parser.add_argument("--password", default="admin123")
    parser.add_argument("--dxf", type=Path, default=DEFAULT_DXF, help="DXF file with missing CAD title block")
    parser.add_argument("--poll-seconds", type=int, default=45)
    parser.add_argument("--mock-host", default="127.0.0.1")
    parser.add_argument("--vision-port", type=int, default=9100)
    parser.add_argument("--ocr-port", type=int, default=9200)
    parser.add_argument(
        "--no-mock-workers",
        action="store_true",
        help="Do not start deterministic mock workers; use the backend's configured workers instead.",
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    dxf_path = args.dxf.resolve()
    if not dxf_path.exists():
        print(f"DXF fixture not found: {dxf_path}", file=sys.stderr)
        return 2

    workers: list[MockWorker] = []
    if not args.no_mock_workers:
        try:
            workers = [
                make_mock_worker("mock-vision-worker", args.mock_host, args.vision_port, MockVisionHandler),
                make_mock_worker("mock-ocr-worker", args.mock_host, args.ocr_port, MockOcrHandler),
            ]
            for worker in workers:
                worker.start()
        except OSError as exc:
            print(
                "Failed to start mock workers. Stop existing services on the mock ports, "
                "rerun with --vision-port/--ocr-port and matching SHIPCAD_VISION_URL/SHIPCAD_OCR_URL, "
                "or rerun with --no-mock-workers if the backend already points to real workers. "
                f"Detail: {exc}",
                file=sys.stderr,
            )
            return 2

    runner = MultimodalEvidenceE2E(args.base_url, args.username, args.password, args.poll_seconds)
    try:
        result = runner.run(dxf_path)
    except Exception as exc:
        print(f"Multimodal evidence E2E failed: {exc}", file=sys.stderr)
        return 1
    finally:
        runner.close()
        for worker in workers:
            worker.stop()

    print("\nMultimodal evidence E2E passed")
    print("-" * 88)
    for key, value in result.items():
        print(f"{key}: {value}")
    print("-" * 88)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
