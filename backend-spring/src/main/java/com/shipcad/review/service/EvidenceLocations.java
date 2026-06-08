package com.shipcad.review.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shipcad.review.domain.EvidenceLocation;
import com.shipcad.review.domain.ParsedEntity;
import com.shipcad.review.dto.ApiDtos.WorkerSummary;
import java.util.List;
import java.util.Map;

public final class EvidenceLocations {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private EvidenceLocations() {
    }

    public static EvidenceLocation cadEntity(ParsedEntity entity, String entityRef) {
        Map<String, Object> raw = raw(entity);
        Map<String, Object> geometry = map(raw.get("geometry"));
        return new EvidenceLocation(
                EvidenceLocation.SPACE_CAD_MODEL,
                EvidenceLocation.REFERENCE_ENTITY,
                value(entityRef),
                firstNonBlank(entity == null ? "" : entity.cadHandle, string(raw.get("handle"))),
                entity == null ? "" : value(entity.layerName),
                point(entity == null ? null : entity.x, entity == null ? null : entity.y),
                bounds(map(geometry.get("bounds"))),
                EvidenceLocation.ORIGIN_BOTTOM_LEFT,
                EvidenceLocation.UNIT_DRAWING,
                null,
                null,
                null,
                null
        );
    }

    public static EvidenceLocation cadLayer(String layerName) {
        return new EvidenceLocation(
                EvidenceLocation.SPACE_CAD_MODEL,
                EvidenceLocation.REFERENCE_LAYER,
                value(layerName),
                "",
                value(layerName),
                null,
                null,
                EvidenceLocation.ORIGIN_BOTTOM_LEFT,
                EvidenceLocation.UNIT_DRAWING,
                null,
                null,
                null,
                null
        );
    }

    public static EvidenceLocation cadSummary(String versionId, WorkerSummary summary) {
        return new EvidenceLocation(
                EvidenceLocation.SPACE_CAD_MODEL,
                EvidenceLocation.REFERENCE_DRAWING,
                value(versionId),
                "",
                "",
                null,
                summary == null ? null : bounds(summary.bounds()),
                EvidenceLocation.ORIGIN_BOTTOM_LEFT,
                EvidenceLocation.UNIT_DRAWING,
                null,
                null,
                null,
                null
        );
    }

    public static EvidenceLocation rasterBox(
            String referenceId,
            List<Double> xyxy,
            Integer imageWidth,
            Integer imageHeight,
            String imageSource,
            Map<String, Double> modelBounds
    ) {
        return new EvidenceLocation(
                EvidenceLocation.SPACE_RASTER_IMAGE,
                EvidenceLocation.REFERENCE_BOUNDING_BOX,
                value(referenceId),
                "",
                "",
                null,
                xyxyBounds(xyxy),
                EvidenceLocation.ORIGIN_TOP_LEFT,
                EvidenceLocation.UNIT_PIXEL,
                imageWidth,
                imageHeight,
                value(imageSource),
                rasterToCadTransform(imageWidth, imageHeight, modelBounds)
        );
    }

    public static EvidenceLocation.Transform rasterToCadTransform(
            Integer imageWidth,
            Integer imageHeight,
            Map<String, Double> modelBounds
    ) {
        if (imageWidth == null || imageHeight == null || imageWidth <= 0 || imageHeight <= 0) {
            return null;
        }
        EvidenceLocation.Bounds target = bounds(modelBounds);
        if (target == null) {
            return null;
        }
        return new EvidenceLocation.Transform(
                EvidenceLocation.SPACE_RASTER_IMAGE,
                EvidenceLocation.SPACE_CAD_MODEL,
                new EvidenceLocation.Bounds(0.0, 0.0, imageWidth.doubleValue(), imageHeight.doubleValue()),
                target,
                EvidenceLocation.ORIGIN_TOP_LEFT,
                EvidenceLocation.ORIGIN_BOTTOM_LEFT
        );
    }

    public static EvidenceLocation.Bounds mapRasterBoundsToCad(EvidenceLocation location) {
        if (location == null || location.bounds() == null || location.transform() == null) {
            return null;
        }
        EvidenceLocation.Transform transform = location.transform();
        EvidenceLocation.Bounds source = transform.sourceBounds();
        EvidenceLocation.Bounds target = transform.targetBounds();
        if (source == null || target == null) {
            return null;
        }
        EvidenceLocation.Point topLeft = mapRasterPointToCad(location.bounds().minX(), location.bounds().minY(), source, target);
        EvidenceLocation.Point bottomRight = mapRasterPointToCad(location.bounds().maxX(), location.bounds().maxY(), source, target);
        if (topLeft == null || bottomRight == null) {
            return null;
        }
        return new EvidenceLocation.Bounds(
                Math.min(topLeft.x(), bottomRight.x()),
                Math.min(topLeft.y(), bottomRight.y()),
                Math.max(topLeft.x(), bottomRight.x()),
                Math.max(topLeft.y(), bottomRight.y())
        );
    }

    private static EvidenceLocation.Point mapRasterPointToCad(
            Double x,
            Double y,
            EvidenceLocation.Bounds source,
            EvidenceLocation.Bounds target
    ) {
        if (x == null || y == null || zeroWidth(source) || zeroHeight(source) || zeroWidth(target) || zeroHeight(target)) {
            return null;
        }
        double xRatio = (x - source.minX()) / (source.maxX() - source.minX());
        double yRatio = (y - source.minY()) / (source.maxY() - source.minY());
        double modelX = target.minX() + xRatio * (target.maxX() - target.minX());
        double modelY = target.maxY() - yRatio * (target.maxY() - target.minY());
        return new EvidenceLocation.Point(modelX, modelY);
    }

    private static boolean zeroWidth(EvidenceLocation.Bounds bounds) {
        return bounds == null || bounds.minX() == null || bounds.maxX() == null || bounds.maxX().equals(bounds.minX());
    }

    private static boolean zeroHeight(EvidenceLocation.Bounds bounds) {
        return bounds == null || bounds.minY() == null || bounds.maxY() == null || bounds.maxY().equals(bounds.minY());
    }

    private static EvidenceLocation.Bounds xyxyBounds(List<Double> xyxy) {
        if (xyxy == null || xyxy.size() < 4) {
            return null;
        }
        return new EvidenceLocation.Bounds(
                min(xyxy.get(0), xyxy.get(2)),
                min(xyxy.get(1), xyxy.get(3)),
                max(xyxy.get(0), xyxy.get(2)),
                max(xyxy.get(1), xyxy.get(3))
        );
    }

    private static EvidenceLocation.Bounds bounds(Map<?, ?> value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        Double minX = number(value.get("minX"));
        Double minY = number(value.get("minY"));
        Double maxX = number(value.get("maxX"));
        Double maxY = number(value.get("maxY"));
        if (minX == null || minY == null || maxX == null || maxY == null) {
            return null;
        }
        return new EvidenceLocation.Bounds(min(minX, maxX), min(minY, maxY), max(minX, maxX), max(minY, maxY));
    }

    private static EvidenceLocation.Point point(Double x, Double y) {
        if (x == null || y == null) {
            return null;
        }
        return new EvidenceLocation.Point(x, y);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    private static Map<String, Object> raw(ParsedEntity entity) {
        if (entity == null || entity.rawJson == null || entity.rawJson.isBlank()) {
            return Map.of();
        }
        try {
            return MAPPER.readValue(entity.rawJson, MAP_TYPE);
        } catch (Exception exception) {
            return Map.of();
        }
    }

    private static Double number(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String string && !string.isBlank()) {
            try {
                return Double.parseDouble(string);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static String string(Object value) {
        return value == null ? "" : value.toString();
    }

    private static String value(String value) {
        return value == null ? "" : value;
    }

    private static String firstNonBlank(String first, String second) {
        return first != null && !first.isBlank() ? first : value(second);
    }

    private static double min(Double left, Double right) {
        return Math.min(left, right);
    }

    private static double max(Double left, Double right) {
        return Math.max(left, right);
    }
}
