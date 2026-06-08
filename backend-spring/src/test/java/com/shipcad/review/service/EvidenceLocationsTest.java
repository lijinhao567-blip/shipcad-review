package com.shipcad.review.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.shipcad.review.domain.EvidenceLocation;
import com.shipcad.review.domain.EvidenceLocationConverter;
import com.shipcad.review.domain.ParsedEntity;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class EvidenceLocationsTest {
    @Test
    void createsCadEntityLocationFromStructuredParserData() {
        ParsedEntity entity = new ParsedEntity();
        entity.id = "entity_1";
        entity.layerName = "S-HULL";
        entity.cadHandle = "2A";
        entity.x = 10.0;
        entity.y = 20.0;
        entity.rawJson = """
                {
                  "handle": "2A",
                  "geometry": {
                    "kind": "line",
                    "bounds": {"minX": 10, "minY": 20, "maxX": 50, "maxY": 60}
                  }
                }
                """;

        EvidenceLocation location = EvidenceLocations.cadEntity(entity, entity.id);

        assertThat(location.coordinateSpace()).isEqualTo(EvidenceLocation.SPACE_CAD_MODEL);
        assertThat(location.referenceType()).isEqualTo(EvidenceLocation.REFERENCE_ENTITY);
        assertThat(location.referenceId()).isEqualTo("entity_1");
        assertThat(location.cadHandle()).isEqualTo("2A");
        assertThat(location.layerName()).isEqualTo("S-HULL");
        assertThat(location.anchor()).isEqualTo(new EvidenceLocation.Point(10.0, 20.0));
        assertThat(location.bounds()).isEqualTo(new EvidenceLocation.Bounds(10.0, 20.0, 50.0, 60.0));
    }

    @Test
    void mapsTopLeftRasterBoundsIntoBottomLeftCadCoordinates() {
        EvidenceLocation location = EvidenceLocations.rasterBox(
                "symbol:welding_symbol#0",
                List.of(100.0, 50.0, 300.0, 150.0),
                1000,
                500,
                "rendered-version-image",
                Map.of("minX", 0.0, "minY", 0.0, "maxX", 200.0, "maxY", 100.0)
        );

        EvidenceLocation.Bounds mapped = EvidenceLocations.mapRasterBoundsToCad(location);

        assertThat(location.coordinateSpace()).isEqualTo(EvidenceLocation.SPACE_RASTER_IMAGE);
        assertThat(location.transform()).isNotNull();
        assertThat(mapped.minX()).isEqualTo(20.0);
        assertThat(mapped.minY()).isEqualTo(70.0);
        assertThat(mapped.maxX()).isEqualTo(60.0);
        assertThat(mapped.maxY()).isEqualTo(90.0);
    }

    @Test
    void leavesUploadedImageWithoutCadTransform() {
        EvidenceLocation location = EvidenceLocations.rasterBox(
                "ocr:text#0",
                List.of(1.0, 2.0, 30.0, 40.0),
                640,
                480,
                "uploaded-image",
                Map.of()
        );

        assertThat(location.bounds()).isEqualTo(new EvidenceLocation.Bounds(1.0, 2.0, 30.0, 40.0));
        assertThat(location.transform()).isNull();
        assertThat(EvidenceLocations.mapRasterBoundsToCad(location)).isNull();
    }

    @Test
    void persistsLocationAsJsonWithoutLosingTheContract() {
        EvidenceLocationConverter converter = new EvidenceLocationConverter();
        EvidenceLocation original = EvidenceLocations.rasterBox(
                "symbol:title_block#0",
                List.of(10.0, 20.0, 110.0, 220.0),
                800,
                600,
                "rendered-version-image",
                Map.of("minX", -50.0, "minY", -30.0, "maxX", 150.0, "maxY", 120.0)
        );

        EvidenceLocation restored = converter.convertToEntityAttribute(converter.convertToDatabaseColumn(original));

        assertThat(restored).isEqualTo(original);
    }
}
