package com.shipcad.review.domain;

public record EvidenceLocation(
        String coordinateSpace,
        String referenceType,
        String referenceId,
        String cadHandle,
        String layerName,
        Point anchor,
        Bounds bounds,
        String origin,
        String unit,
        Integer imageWidth,
        Integer imageHeight,
        String imageSource,
        Transform transform
) {
    public static final String SPACE_CAD_MODEL = "CAD_MODEL";
    public static final String SPACE_RASTER_IMAGE = "RASTER_IMAGE";
    public static final String SPACE_VIEWPORT = "VIEWPORT";
    public static final String REFERENCE_ENTITY = "ENTITY";
    public static final String REFERENCE_LAYER = "LAYER";
    public static final String REFERENCE_DRAWING = "DRAWING";
    public static final String REFERENCE_BOUNDING_BOX = "BOUNDING_BOX";
    public static final String ORIGIN_BOTTOM_LEFT = "BOTTOM_LEFT";
    public static final String ORIGIN_TOP_LEFT = "TOP_LEFT";
    public static final String UNIT_DRAWING = "DRAWING_UNIT";
    public static final String UNIT_PIXEL = "PIXEL";
    public static final String UNIT_CSS_PIXEL = "CSS_PIXEL";

    public record Point(Double x, Double y) {
    }

    public record Bounds(Double minX, Double minY, Double maxX, Double maxY) {
    }

    public record Transform(
            String sourceSpace,
            String targetSpace,
            Bounds sourceBounds,
            Bounds targetBounds,
            String sourceOrigin,
            String targetOrigin
    ) {
    }
}
