import base64
import json
from pathlib import Path

from fastapi.testclient import TestClient

from cad_worker.app.main import app


client = TestClient(app)


def test_render_returns_coordinate_metadata_header() -> None:
    sample = Path("samples/dxf/valid_ship_section.dxf")

    with sample.open("rb") as stream:
        response = client.post(
            "/render?width=800&height=600",
            files={"file": (sample.name, stream, "application/dxf")},
        )

    assert response.status_code == 200
    assert response.headers["content-type"].startswith("image/png")
    encoded = response.headers["x-shipcad-render-metadata"]
    metadata = json.loads(base64.urlsafe_b64decode(encoded).decode("utf-8"))
    assert metadata["width"] == 800
    assert metadata["height"] == 600
    assert metadata["modelBounds"]["minX"] < metadata["modelBounds"]["maxX"]
    assert metadata["modelBounds"]["minY"] < metadata["modelBounds"]["maxY"]
