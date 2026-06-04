from pathlib import Path

from cad_worker.app.renderer import render_dxf_to_png


def test_render_valid_sample_to_png(tmp_path: Path) -> None:
    output = tmp_path / "render.png"

    metadata = render_dxf_to_png(Path("samples/dxf/valid_ship_section.dxf"), output, width=800, height=600)

    assert metadata["renderer"] == "ezdxf-matplotlib"
    assert metadata["width"] == 800
    assert metadata["height"] == 600
    assert output.read_bytes().startswith(b"\x89PNG\r\n\x1a\n")
    assert output.stat().st_size > 1000
