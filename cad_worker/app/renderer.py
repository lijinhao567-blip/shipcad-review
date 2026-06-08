from __future__ import annotations

from pathlib import Path
from typing import Any

import ezdxf


class DxfRenderUnavailable(RuntimeError):
    """Raised when DXF rendering dependencies are not installed."""


def render_dxf_to_png(path: Path, output_path: Path, width: int = 1600, height: int = 1200, dpi: int = 150) -> dict[str, Any]:
    if width <= 0 or height <= 0:
        raise ValueError("width and height must be positive")
    if width > 4096 or height > 4096:
        raise ValueError("render size exceeds 4096px MVP limit")

    try:
        import matplotlib

        matplotlib.use("Agg")
        import matplotlib.pyplot as plt
        from ezdxf.addons.drawing import Frontend, RenderContext
        from ezdxf.addons.drawing.matplotlib import MatplotlibBackend
    except ImportError as exc:
        raise DxfRenderUnavailable("DXF rendering requires matplotlib. Install cad_worker requirements.") from exc

    doc = ezdxf.readfile(path)
    modelspace = doc.modelspace()
    if len(modelspace) == 0:
        raise ValueError("DXF modelspace is empty; nothing can be rendered")

    output_path.parent.mkdir(parents=True, exist_ok=True)
    fig = plt.figure(figsize=(width / dpi, height / dpi), dpi=dpi)
    ax = fig.add_axes((0, 0, 1, 1))
    ax.set_axis_off()
    ax.set_aspect("equal")
    fig.patch.set_facecolor("white")
    ax.set_facecolor("white")

    try:
        context = RenderContext(doc)
        backend = MatplotlibBackend(ax)
        Frontend(context, backend).draw_layout(modelspace, finalize=True)
        x_min, x_max = ax.get_xlim()
        y_min, y_max = ax.get_ylim()
        fig.savefig(output_path, dpi=dpi, facecolor="white", edgecolor="white")
    finally:
        plt.close(fig)

    return {
        "renderer": "ezdxf-matplotlib",
        "ezdxfVersion": ezdxf.__version__,
        "width": width,
        "height": height,
        "format": "png",
        "modelBounds": {
            "minX": min(float(x_min), float(x_max)),
            "minY": min(float(y_min), float(y_max)),
            "maxX": max(float(x_min), float(x_max)),
            "maxY": max(float(y_min), float(y_max)),
        },
    }
