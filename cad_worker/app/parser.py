from __future__ import annotations

from collections import Counter
from pathlib import Path
from typing import Any

import ezdxf


def parse_dxf(path: Path) -> dict[str, Any]:
    doc = ezdxf.readfile(path)
    modelspace = doc.modelspace()
    entities: list[dict[str, Any]] = []

    for entity in modelspace:
        entities.extend(_entity_records(entity))

    layer_names = sorted(layer.dxf.name for layer in doc.layers)
    layer_counts = Counter(entity["layer"] for entity in entities)
    type_counts = Counter(entity["entityType"] for entity in entities)
    empty_layers = sorted(layer for layer in layer_names if layer not in layer_counts)
    texts = [entity["text"] for entity in entities if entity["text"]]
    blocks = [entity["blockName"] for entity in entities if entity["blockName"]]

    return {
        "entities": entities,
        "summary": {
            "entityCount": len(entities),
            "typeCounts": dict(type_counts),
            "layerCounts": dict(layer_counts),
            "layers": layer_names,
            "emptyLayers": empty_layers,
            "texts": texts[:200],
            "blocks": blocks[:200],
            "bounds": _bounds(entities),
            "parser": "ezdxf",
            "ezdxfVersion": ezdxf.__version__,
        },
    }


def _entity_records(entity: Any) -> list[dict[str, Any]]:
    records = [_entity_record(entity)]
    if entity.dxftype() == "INSERT":
        block_name = getattr(entity.dxf, "name", "") or ""
        for attrib in getattr(entity, "attribs", []):
            records.append(_entity_record(attrib, parent_block_name=block_name))
    return records


def _entity_record(entity: Any, parent_block_name: str = "") -> dict[str, Any]:
    point = _entity_point(entity)
    x = None
    y = None
    if point is not None:
        x, y = float(point[0]), float(point[1])
    geometry = _entity_geometry(entity, parent_block_name)
    bounds = _geometry_bounds(geometry)
    if bounds is not None:
        geometry["bounds"] = bounds

    return {
        "handle": getattr(entity.dxf, "handle", "") or "",
        "entityType": entity.dxftype(),
        "layer": getattr(entity.dxf, "layer", "0") or "0",
        "text": _entity_text(entity),
        "blockName": _entity_block_name(entity, parent_block_name),
        "x": x,
        "y": y,
        "geometry": geometry,
    }


def _entity_text(entity: Any) -> str:
    dxftype = entity.dxftype()
    if dxftype in {"TEXT", "MTEXT", "ATTRIB"}:
        if hasattr(entity, "plain_text"):
            return entity.plain_text()
        return getattr(entity.dxf, "text", "") or ""
    if dxftype == "DIMENSION":
        explicit_text = getattr(entity.dxf, "text", "") or ""
        if explicit_text and explicit_text != "<>":
            return explicit_text
        measurement = _dimension_measurement(entity)
        return str(measurement) if measurement is not None else explicit_text
    return ""


def _entity_block_name(entity: Any, parent_block_name: str) -> str:
    if entity.dxftype() == "INSERT":
        return getattr(entity.dxf, "name", "") or ""
    if entity.dxftype() == "ATTRIB":
        return parent_block_name
    return ""


def _entity_point(entity: Any) -> Any | None:
    for attr in ("insert", "start", "center", "location", "text_midpoint", "defpoint"):
        if hasattr(entity.dxf, attr):
            return getattr(entity.dxf, attr)
    return None


def _entity_geometry(entity: Any, parent_block_name: str = "") -> dict[str, Any]:
    dxftype = entity.dxftype()
    try:
        if dxftype == "LINE":
            return {
                "kind": "line",
                "start": _point(entity.dxf.start),
                "end": _point(entity.dxf.end),
            }
        if dxftype == "CIRCLE":
            return {
                "kind": "circle",
                "center": _point(entity.dxf.center),
                "radius": float(entity.dxf.radius),
            }
        if dxftype == "ARC":
            return {
                "kind": "arc",
                "center": _point(entity.dxf.center),
                "radius": float(entity.dxf.radius),
                "startAngle": float(entity.dxf.start_angle),
                "endAngle": float(entity.dxf.end_angle),
            }
        if dxftype in {"TEXT", "MTEXT"}:
            return {
                "kind": "text",
                "insert": _point(_entity_point(entity)),
                "height": float(getattr(entity.dxf, "height", 2.5)),
            }
        if dxftype == "ATTRIB":
            return {
                "kind": "text",
                "insert": _point(_entity_point(entity)),
                "height": float(getattr(entity.dxf, "height", 2.5)),
                "tag": getattr(entity.dxf, "tag", "") or "",
                "blockName": parent_block_name,
            }
        if dxftype == "INSERT":
            return {
                "kind": "insert",
                "insert": _point(entity.dxf.insert),
                "name": getattr(entity.dxf, "name", ""),
            }
        if dxftype == "DIMENSION":
            geometry: dict[str, Any] = {
                "kind": "dimension",
                "dimensionType": int(getattr(entity.dxf, "dimtype", 0)),
                "text": getattr(entity.dxf, "text", "") or "",
            }
            measurement = _dimension_measurement(entity)
            if measurement is not None:
                geometry["measurement"] = measurement
            for dxf_attr, key in (
                ("defpoint", "definitionPoint"),
                ("defpoint2", "extensionLine1"),
                ("defpoint3", "extensionLine2"),
                ("text_midpoint", "textMidpoint"),
            ):
                if hasattr(entity.dxf, dxf_attr):
                    geometry[key] = _point(getattr(entity.dxf, dxf_attr))
            return geometry
        if dxftype == "LWPOLYLINE":
            return {"kind": "polyline", "points": [_point(point) for point in entity.get_points("xy")]}
        if dxftype == "POLYLINE":
            return {"kind": "polyline", "points": [_point(vertex.dxf.location) for vertex in entity.vertices]}
    except Exception:
        return {"kind": dxftype.lower(), "unsupported": True}
    return {"kind": dxftype.lower()}


def _dimension_measurement(entity: Any) -> float | None:
    try:
        return float(entity.get_measurement())
    except Exception:
        return None


def _point(point: Any) -> list[float]:
    if point is None:
        return []
    return [float(point[0]), float(point[1])]


def _bounds(entities: list[dict[str, Any]]) -> dict[str, float] | None:
    points = []
    for entity in entities:
        geometry = entity.get("geometry") or {}
        points.extend(_geometry_points(geometry))
        if entity["x"] is not None and entity["y"] is not None:
            points.append((entity["x"], entity["y"]))
    if not points:
        return None
    xs = [point[0] for point in points]
    ys = [point[1] for point in points]
    return {"minX": min(xs), "minY": min(ys), "maxX": max(xs), "maxY": max(ys)}


def _geometry_bounds(geometry: dict[str, Any]) -> dict[str, float] | None:
    points = _geometry_points(geometry)
    if not points:
        return None
    xs = [point[0] for point in points]
    ys = [point[1] for point in points]
    return {"minX": min(xs), "minY": min(ys), "maxX": max(xs), "maxY": max(ys)}


def _geometry_points(geometry: dict[str, Any]) -> list[tuple[float, float]]:
    kind = geometry.get("kind")
    if kind == "line":
        return [_tuple_point(geometry.get("start")), _tuple_point(geometry.get("end"))]
    if kind in {"text", "insert"}:
        return [_tuple_point(geometry.get("insert"))]
    if kind in {"circle", "arc"}:
        center = _tuple_point(geometry.get("center"))
        radius = float(geometry.get("radius", 0))
        return [(center[0] - radius, center[1] - radius), (center[0] + radius, center[1] + radius)]
    if kind == "polyline":
        return [_tuple_point(point) for point in geometry.get("points", [])]
    if kind == "dimension":
        points = []
        for key in ("definitionPoint", "extensionLine1", "extensionLine2", "textMidpoint"):
            value = geometry.get(key)
            if value:
                points.append(_tuple_point(value))
        return points
    return []


def _tuple_point(value: Any) -> tuple[float, float]:
    if not value:
        return (0.0, 0.0)
    return (float(value[0]), float(value[1]))
