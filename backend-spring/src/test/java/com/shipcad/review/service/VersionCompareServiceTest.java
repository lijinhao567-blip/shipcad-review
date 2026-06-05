package com.shipcad.review.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.shipcad.review.domain.DrawingVersion;
import com.shipcad.review.dto.ApiDtos.VersionCompareResponse;
import com.shipcad.review.dto.ApiDtos.WorkerSummary;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class VersionCompareServiceTest {
    private final VersionCompareService service = new VersionCompareService();

    @Test
    void buildsReadableVersionDeltaWithRisksAndFocus() {
        DrawingVersion leftVersion = version("left", "V1");
        DrawingVersion rightVersion = version("right", "V2");
        WorkerSummary left = summary(
                5,
                Map.of("LINE", 3, "TEXT", 1, "INSERT", 1),
                Map.of("S-HULL", 3, "TITLE", 2),
                List.of("S-HULL", "TITLE"),
                List.of(),
                List.of("REV V1"),
                List.of("TITLE_BLOCK")
        );
        WorkerSummary right = summary(
                4,
                Map.of("LINE", 2, "TEXT", 2),
                Map.of("S-HULL", 2, "EMPTY-OLD", 0),
                List.of("S-HULL", "EMPTY-OLD"),
                List.of("EMPTY-OLD"),
                List.of("REV V2", "TBD bracket"),
                List.of()
        );

        VersionCompareResponse result = service.compare(leftVersion, left, rightVersion, right);

        assertThat(result.entityCountDelta()).isEqualTo(-1);
        assertThat(result.addedLayers()).containsExactly("EMPTY-OLD");
        assertThat(result.removedLayers()).containsExactly("TITLE");
        assertThat(result.addedTexts()).containsExactly("REV V2", "TBD bracket");
        assertThat(result.removedTexts()).containsExactly("REV V1");
        assertThat(result.typeDeltas()).extracting(delta -> delta.name() + ":" + delta.delta())
                .containsExactly("INSERT:-1", "LINE:-1", "TEXT:1");
        assertThat(result.riskHints()).anySatisfy(hint -> assertThat(hint).contains("实体数量减少"));
        assertThat(result.riskHints()).anySatisfy(hint -> assertThat(hint).contains("占位内容"));
        assertThat(result.riskHints()).anySatisfy(hint -> assertThat(hint).contains("TITLE_BLOCK"));
        assertThat(result.reviewFocus()).anySatisfy(focus -> assertThat(focus).contains("新增/删除图层"));
        assertThat(result.summary()).contains("从 V1 到 V2");
    }

    private DrawingVersion version(String id, String versionNo) {
        DrawingVersion version = new DrawingVersion();
        version.id = id;
        version.versionNo = versionNo;
        version.fileName = versionNo + ".dxf";
        version.parseStatus = "SUCCESS";
        return version;
    }

    private WorkerSummary summary(
            int entityCount,
            Map<String, Integer> typeCounts,
            Map<String, Integer> layerCounts,
            List<String> layers,
            List<String> emptyLayers,
            List<String> texts,
            List<String> blocks
    ) {
        return new WorkerSummary(
                entityCount,
                typeCounts,
                layerCounts,
                layers,
                emptyLayers,
                texts,
                blocks,
                Map.of(),
                "ezdxf",
                "1.4.4"
        );
    }
}
