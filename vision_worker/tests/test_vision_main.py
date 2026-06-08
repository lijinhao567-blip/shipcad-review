import pytest

from vision_worker.app.main import capabilities, load_model


def test_capabilities_reports_unconfigured_model(monkeypatch):
    monkeypatch.delenv("YOLO_MODEL_PATH", raising=False)

    result = capabilities()

    assert result["engine"] == "ultralytics-yolov8"
    assert result["license"] == "AGPL-3.0"
    assert result["modelConfigured"] is False
    assert result["modelPath"] == ""


def test_load_model_requires_configured_path(monkeypatch):
    monkeypatch.delenv("YOLO_MODEL_PATH", raising=False)
    load_model.cache_clear()

    with pytest.raises(RuntimeError, match="YOLO_MODEL_PATH is not configured"):
        load_model()


def test_load_model_rejects_missing_weight_file(monkeypatch, tmp_path):
    monkeypatch.setenv("YOLO_MODEL_PATH", str(tmp_path / "missing.pt"))
    load_model.cache_clear()

    with pytest.raises(RuntimeError, match="does not exist"):
        load_model()
