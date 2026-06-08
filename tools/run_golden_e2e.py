from __future__ import annotations

import argparse
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
DEFAULT_MANIFEST = ROOT / "datasets" / "rules" / "expected.json"


@dataclass
class CaseResult:
    case_id: str
    ok: bool
    expected_rules: list[str]
    actual_rules: list[str]
    message: str


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


class MockEvidenceProfile:
    vision: dict[str, Any] = {"detections": [], "imageWidth": 640, "imageHeight": 480, "engine": "mock-yolov8"}
    ocr: dict[str, Any] = {"regions": [], "imageWidth": 640, "imageHeight": 480, "engine": "mock-ocr", "language": "eng"}


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
        self.write_json(MockEvidenceProfile.vision)

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
        self.send_header("Connection", "close")
        self.end_headers()
        self.wfile.write(body)
        self.wfile.flush()
        self.close_connection = True


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
        self.write_json(MockEvidenceProfile.ocr)

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
        self.send_header("Connection", "close")
        self.end_headers()
        self.wfile.write(body)
        self.wfile.flush()
        self.close_connection = True


class GoldenE2E:
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
        token = response.json()["token"]
        self.headers = {"Authorization": f"Bearer {token}"}

    def create_project(self) -> dict[str, Any]:
        stamp = datetime.now(timezone.utc).strftime("%Y%m%d%H%M%S")
        return self.request(
            "POST",
            "/api/projects",
            json={
                "name": f"Golden Dataset E2E {stamp}",
                "shipNo": f"GOLDEN-{stamp}",
                "owner": "Automated Acceptance",
                "description": "Generated by tools/run_golden_e2e.py",
            },
        ).json()

    def create_drawing(self, project_id: str, case: dict[str, Any]) -> dict[str, Any]:
        return self.request(
            "POST",
            "/api/drawings",
            json={
                "projectId": project_id,
                "drawingNo": case["drawingNo"],
                "title": case["title"],
                "discipline": "Hull Structure",
            },
        ).json()

    def upload_version(self, drawing_id: str, case: dict[str, Any], file_path: Path) -> dict[str, Any]:
        with file_path.open("rb") as file:
            response = self.request(
                "POST",
                "/api/versions/upload",
                data={"drawingId": drawing_id, "versionNo": case["versionNo"]},
                files={"file": (file_path.name, file, "application/dxf")},
            )
        return response.json()

    def create_review_task(self, version_id: str, case: dict[str, Any]) -> dict[str, Any]:
        payload = {"versionId": version_id}
        payload.update(case.get("reviewTask") or {})
        return self.request("POST", "/api/review-tasks", json=payload).json()

    def create_report(self, task_id: str) -> dict[str, Any]:
        return self.request("POST", "/api/reports", json={"taskId": task_id}).json()

    def report_download_check(self, report: dict[str, Any]) -> None:
        response = self.request("GET", f"/api/reports/{report['id']}/download")
        if len(response.content) == 0:
            raise AssertionError("report download endpoint returned an empty body")
        if "text/markdown" not in response.headers.get("content-type", ""):
            raise AssertionError(f"report download returned unexpected content type: {response.headers.get('content-type')}")
        if "attachment" not in response.headers.get("content-disposition", ""):
            raise AssertionError("report download is missing attachment content disposition")
        expected_content = report.get("content") or ""
        if expected_content and response.text != expected_content:
            raise AssertionError("report download content does not match generated report content")

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

    def task_steps(self, task_id: str) -> list[dict[str, Any]]:
        return self.request("GET", f"/api/review-tasks/{task_id}/steps").json()

    def issues(self, task_id: str) -> list[dict[str, Any]]:
        return self.request("GET", "/api/issues", params={"taskId": task_id}).json()

    def entities(self, version_id: str) -> list[dict[str, Any]]:
        return self.request("GET", f"/api/versions/{version_id}/entities").json()

    def versions(self) -> list[dict[str, Any]]:
        return self.request("GET", "/api/versions").json()

    def file_head_check(self, version_id: str) -> None:
        response = self.request("GET", f"/api/versions/{version_id}/file")
        if len(response.content) == 0:
            raise AssertionError("version file endpoint returned an empty body")

    def run_case(self, project_id: str, manifest_dir: Path, case: dict[str, Any], evict_upload_cache: bool) -> CaseResult:
        case_id = case["id"]
        expected_rules = sorted(case.get("expectedRuleCodes", []))
        try:
            apply_mock_profiles(case)
            file_path = (manifest_dir / case["file"]).resolve()
            if not file_path.exists():
                raise FileNotFoundError(file_path)
            drawing = self.create_drawing(project_id, case)
            version = self.upload_version(drawing["id"], case, file_path)
            self.assert_version_storage(version)
            if evict_upload_cache:
                self.evict_local_cache(version)
            self.file_head_check(version["id"])
            if evict_upload_cache:
                self.evict_local_cache(version)
            task = self.create_review_task(version["id"], case)
            finished = self.wait_for_task(task["id"])
            if finished["status"] != "FINISHED":
                raise AssertionError(f"review task failed: {finished.get('errorMessage')}")
            self.assert_task_steps(task["id"], expected_task_steps(case))

            actual_issues = self.issues(task["id"])
            actual_rules = sorted({issue["ruleCode"] for issue in actual_issues})
            self.assert_issue_count(case, actual_issues)
            self.assert_rules(case, expected_rules, actual_rules)
            self.assert_parser_expectations(version["id"], case.get("parserExpectations", {}))
            self.assert_issue_evidence(version["id"], case, actual_issues)
            report = self.create_report(task["id"])
            self.assert_report_storage(report)
            self.assert_report(case, actual_issues, report)
            if evict_upload_cache:
                self.evict_report_cache(report)
            self.report_download_check(report)
            return CaseResult(case_id, True, expected_rules, actual_rules, "ok")
        except Exception as exc:
            return CaseResult(case_id, False, expected_rules, [], str(exc))

    def assert_rules(self, case: dict[str, Any], expected_rules: list[str], actual_rules: list[str]) -> None:
        if case.get("allowUnexpectedRuleCodes", False):
            missing = sorted(set(expected_rules) - set(actual_rules))
            if missing:
                raise AssertionError(f"missing expected rule codes: {missing}; actual={actual_rules}")
            return
        if actual_rules != expected_rules:
            raise AssertionError(f"expected rule codes {expected_rules}, got {actual_rules}")

    def assert_issue_count(self, case: dict[str, Any], actual_issues: list[dict[str, Any]]) -> None:
        expected_count = case.get("expectedIssueCount")
        if expected_count is None:
            return
        if len(actual_issues) != expected_count:
            actual_rules = sorted(issue.get("ruleCode") for issue in actual_issues)
            raise AssertionError(f"expected {expected_count} issues, got {len(actual_issues)}: {actual_rules}")

    def assert_version_storage(self, version: dict[str, Any]) -> None:
        storage_mode = version.get("storageMode") or ""
        object_key = version.get("fileObjectKey") or ""
        file_path = version.get("filePath") or ""
        if storage_mode not in {"local", "s3"}:
            raise AssertionError(f"version {version.get('id')} returned invalid storageMode={storage_mode!r}")
        if not object_key:
            raise AssertionError(f"version {version.get('id')} is missing fileObjectKey")
        if not file_path:
            raise AssertionError(f"version {version.get('id')} is missing local filePath/cache path")

    def assert_report_storage(self, report: dict[str, Any]) -> None:
        storage_mode = report.get("storageMode") or ""
        object_key = report.get("contentObjectKey") or ""
        content_path = report.get("contentPath") or ""
        size_bytes = report.get("contentSizeBytes") or 0
        if storage_mode not in {"local", "s3"}:
            raise AssertionError(f"report {report.get('id')} returned invalid storageMode={storage_mode!r}")
        if not object_key:
            raise AssertionError(f"report {report.get('id')} is missing contentObjectKey")
        if not content_path:
            raise AssertionError(f"report {report.get('id')} is missing contentPath/cache path")
        if size_bytes <= 0:
            raise AssertionError(f"report {report.get('id')} returned invalid contentSizeBytes={size_bytes!r}")

    def evict_local_cache(self, version: dict[str, Any]) -> None:
        file_path = version.get("filePath") or ""
        if not file_path:
            raise AssertionError(f"version {version.get('id')} has no local cache path to evict")
        path = Path(file_path)
        if path.exists():
            path.unlink()

    def evict_report_cache(self, report: dict[str, Any]) -> None:
        content_path = report.get("contentPath") or ""
        if not content_path:
            raise AssertionError(f"report {report.get('id')} has no local cache path to evict")
        path = Path(content_path)
        if path.exists():
            path.unlink()

    def assert_issue_evidence(self, version_id: str, case: dict[str, Any], issues: list[dict[str, Any]]) -> None:
        parsed_entities = {entity["id"]: entity for entity in self.entities(version_id)}
        for issue in issues:
            entity_ref = issue.get("entityRef") or ""
            if entity_ref and entity_ref not in parsed_entities:
                raise AssertionError(f"issue {issue['id']} references missing entityRef={entity_ref}")
            evidence_items = issue.get("evidences") or []
            evidence_types = {item.get("evidenceType") for item in evidence_items}
            if "RULE_RESULT" not in evidence_types:
                raise AssertionError(f"issue {issue['id']} is missing RULE_RESULT evidence")
            if "KNOWLEDGE_CLAUSE" not in evidence_types:
                raise AssertionError(f"issue {issue['id']} is missing KNOWLEDGE_CLAUSE evidence")
            explanation = issue.get("aiExplanation") or {}
            if not explanation.get("summary") or not explanation.get("reason") or not explanation.get("basis"):
                raise AssertionError(f"issue {issue['id']} is missing AI evidence explanation")

        expected_evidence = case.get("expectedEvidence") or {}
        if not expected_evidence:
            return
        issues_by_rule: dict[str, list[dict[str, Any]]] = {}
        for issue in issues:
            issues_by_rule.setdefault(issue["ruleCode"], []).append(issue)

        for rule_code, expected in expected_evidence.items():
            candidates = issues_by_rule.get(rule_code) or []
            if not candidates:
                raise AssertionError(f"no issue found for expected evidence rule {rule_code}")
            issue = candidates[0]
            expected_layer = expected.get("layerName")
            if expected_layer is not None and issue.get("layerName") != expected_layer:
                raise AssertionError(f"{rule_code} expected layerName={expected_layer}, got {issue.get('layerName')}")

            entity_ref = issue.get("entityRef") or ""
            if expected.get("requireEntityRef") and not entity_ref:
                raise AssertionError(f"{rule_code} expected a non-empty entityRef")
            if entity_ref:
                entity = parsed_entities[entity_ref]
                if expected_layer is not None and entity.get("layerName") != expected_layer:
                    raise AssertionError(f"{rule_code} entityRef layer mismatch: expected {expected_layer}, got {entity.get('layerName')}")
                self.assert_embedded_evidence(issue, "CAD_ENTITY", entity_ref)
            elif expected_layer is not None:
                self.assert_embedded_evidence(issue, "CAD_LAYER", expected_layer)

            for evidence_type in expected.get("requireEvidenceTypes") or []:
                matches = self.assert_issue_has_evidence_type(issue, evidence_type)
                expected_space = expected.get("locationCoordinateSpace")
                if expected_space and not any((item.get("location") or {}).get("coordinateSpace") == expected_space for item in matches):
                    raise AssertionError(f"{rule_code} expected {evidence_type} evidence in {expected_space} space")

    def assert_embedded_evidence(self, issue: dict[str, Any], evidence_type: str, source_id: str) -> None:
        matches = [
            evidence
            for evidence in (issue.get("evidences") or [])
            if evidence.get("evidenceType") == evidence_type and evidence.get("sourceId") == source_id
        ]
        if not matches:
            raise AssertionError(f"{issue['ruleCode']} expected {evidence_type} evidence with sourceId={source_id}")

    def assert_issue_has_evidence_type(self, issue: dict[str, Any], evidence_type: str) -> list[dict[str, Any]]:
        matches = [
            evidence
            for evidence in (issue.get("evidences") or [])
            if evidence.get("evidenceType") == evidence_type
        ]
        if not matches:
            raise AssertionError(f"{issue['ruleCode']} expected {evidence_type} evidence")
        return matches

    def assert_parser_expectations(self, version_id: str, expectations: dict[str, Any]) -> None:
        version = next((item for item in self.versions() if item["id"] == version_id), None)
        if not version:
            raise AssertionError(f"version {version_id} not found after review")
        summary = json.loads(version.get("parseSummaryJson") or "{}")
        min_entities = expectations.get("minEntityCount")
        if min_entities is not None and summary.get("entityCount", 0) < min_entities:
            raise AssertionError(f"entityCount {summary.get('entityCount')} < {min_entities}")
        layers = set(summary.get("layers") or [])
        missing_layers = sorted(set(expectations.get("requiredLayers") or []) - layers)
        if missing_layers:
            raise AssertionError(f"missing parsed layers: {missing_layers}")
        blocks = set(summary.get("blocks") or [])
        missing_blocks = sorted(set(expectations.get("requiredBlocks") or []) - blocks)
        if missing_blocks:
            raise AssertionError(f"missing parsed blocks: {missing_blocks}")
        type_counts = summary.get("typeCounts") or {}
        missing_types = sorted(
            entity_type
            for entity_type in (expectations.get("requiredEntityTypes") or [])
            if type_counts.get(entity_type, 0) <= 0
        )
        if missing_types:
            raise AssertionError(f"missing parsed entity types: {missing_types}; typeCounts={type_counts}")

    def assert_report(self, case: dict[str, Any], issues: list[dict[str, Any]], report: dict[str, Any]) -> None:
        content = report.get("content") or ""
        if "解析证据摘要" not in content:
            raise AssertionError("report is missing parser evidence summary")
        if "问题证据详情" not in content:
            raise AssertionError("report is missing issue evidence details")
        expected_rules = case.get("expectedRuleCodes") or []
        if not expected_rules and "未发现当前规则集命中的问题" not in content:
            raise AssertionError("clean report is missing no-issue summary")
        for rule_code in expected_rules:
            if rule_code not in content:
                raise AssertionError(f"report is missing expected rule code {rule_code}")
        for issue in issues:
            entity_ref = issue.get("entityRef") or ""
            if entity_ref and f"entityRef={entity_ref}" not in content:
                raise AssertionError(f"report is missing entity evidence {entity_ref}")
            layer_name = issue.get("layerName") or ""
            if layer_name and layer_name not in content:
                raise AssertionError(f"report is missing layer evidence {layer_name}")
        if issues and "AI辅助解释" not in content:
            raise AssertionError("report is missing AI evidence explanations")

    def assert_task_steps(self, task_id: str, expected: dict[str, str]) -> None:
        steps = self.task_steps(task_id)
        by_code = {step.get("stepCode"): step for step in steps}
        missing = sorted(set(expected) - set(by_code))
        if missing:
            raise AssertionError(f"review task {task_id} is missing steps {missing}; actual={sorted(by_code)}")
        mismatches = {
            code: {"expected": status, "actual": by_code[code].get("status")}
            for code, status in expected.items()
            if by_code[code].get("status") != status
        }
        if mismatches:
            raise AssertionError(f"review task {task_id} step status mismatch: {mismatches}")


def load_manifest(path: Path) -> list[dict[str, Any]]:
    return json.loads(path.read_text(encoding="utf-8"))


def apply_mock_profiles(case: dict[str, Any]) -> None:
    MockEvidenceProfile.vision = {
        "detections": [],
        "imageWidth": 640,
        "imageHeight": 480,
        "engine": "mock-yolov8",
    }
    MockEvidenceProfile.vision.update(case.get("mockVision") or {})
    MockEvidenceProfile.ocr = {
        "regions": [],
        "imageWidth": 640,
        "imageHeight": 480,
        "engine": "mock-ocr",
        "language": "eng",
    }
    MockEvidenceProfile.ocr.update(case.get("mockOcr") or {})


def expected_task_steps(case: dict[str, Any]) -> dict[str, str]:
    task = case.get("reviewTask") or {}
    auto_vision = bool(task.get("autoVision"))
    auto_ocr = bool(task.get("autoOcr"))
    return {
        "PARSE": "SUCCESS",
        "RENDER": "SUCCESS" if auto_vision or auto_ocr else "SKIPPED",
        "VISION": "SUCCESS" if auto_vision else "SKIPPED",
        "OCR": "SUCCESS" if auto_ocr else "SKIPPED",
        "RULES": "SUCCESS",
    }


def needs_mock_workers(cases: list[dict[str, Any]]) -> bool:
    return any((case.get("reviewTask") or {}).get("autoVision") or (case.get("reviewTask") or {}).get("autoOcr") for case in cases)


def start_mock_worker(name: str, port: int, handler: type[BaseHTTPRequestHandler]) -> MockWorker:
    server = ThreadingHTTPServer(("127.0.0.1", port), handler)
    thread = threading.Thread(target=server.serve_forever, name=name, daemon=True)
    worker = MockWorker(name, server, thread)
    worker.start()
    return worker


def print_results(results: list[CaseResult]) -> None:
    print("\nGolden dataset E2E results")
    print("-" * 88)
    for result in results:
        status = "PASS" if result.ok else "FAIL"
        print(f"{status:4} {result.case_id:24} expected={result.expected_rules} actual={result.actual_rules} {result.message}")
    print("-" * 88)
    passed = sum(1 for result in results if result.ok)
    print(f"{passed}/{len(results)} cases passed")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Run the DXF golden dataset against the live backend and CAD worker.")
    parser.add_argument("--base-url", default="http://127.0.0.1:8080", help="Spring Boot API base URL")
    parser.add_argument("--manifest", type=Path, default=DEFAULT_MANIFEST, help="Golden dataset manifest path")
    parser.add_argument("--username", default="admin")
    parser.add_argument("--password", default="admin123")
    parser.add_argument("--poll-seconds", type=int, default=45)
    parser.add_argument("--keep-going", action="store_true", help="Run remaining cases after a failure")
    parser.add_argument("--mock-vision-port", type=int, default=9100, help="Mock Vision Worker port for autoVision golden cases")
    parser.add_argument("--mock-ocr-port", type=int, default=9200, help="Mock OCR Worker port for autoOcr golden cases")
    parser.add_argument(
        "--evict-upload-cache",
        action="store_true",
        help="Delete uploaded version/report local caches before download and review, forcing object-storage reads.",
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    manifest_path = args.manifest.resolve()
    cases = load_manifest(manifest_path)
    runner = GoldenE2E(args.base_url, args.username, args.password, args.poll_seconds)
    results: list[CaseResult] = []
    workers: list[MockWorker] = []
    try:
        if needs_mock_workers(cases):
            workers.append(start_mock_worker("mock-vision-worker", args.mock_vision_port, MockVisionHandler))
            workers.append(start_mock_worker("mock-ocr-worker", args.mock_ocr_port, MockOcrHandler))
        runner.login()
        project = runner.create_project()
        for case in cases:
            result = runner.run_case(project["id"], manifest_path.parent, case, args.evict_upload_cache)
            results.append(result)
            if not result.ok and not args.keep_going:
                break
    except Exception as exc:
        print(f"Golden dataset E2E setup failed: {exc}", file=sys.stderr)
        return 2
    finally:
        runner.close()
        for worker in workers:
            worker.stop()

    print_results(results)
    return 0 if results and all(result.ok for result in results) else 1


if __name__ == "__main__":
    raise SystemExit(main())
