package com.shipcad.review.service;

import com.shipcad.review.domain.DrawingVersion;
import com.shipcad.review.domain.ParsedEntity;
import com.shipcad.review.domain.ReviewIssue;
import com.shipcad.review.domain.ReviewRule;
import com.shipcad.review.domain.Severity;
import com.shipcad.review.dto.ApiDtos.WorkerSummary;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RuleEngineTest {
    @Test
    void detectsCommercialMvpRules() {
        DrawingVersion version = new DrawingVersion();
        version.id = "version_test";
        version.versionNo = "draft";

        ParsedEntity entity = new ParsedEntity();
        entity.id = "entity_test";
        entity.layerName = "bad-layer";
        entity.textValue = "TBD 待定开孔";

        WorkerSummary summary = new WorkerSummary(
                2,
                Map.of("LINE", 1, "TEXT", 1),
                Map.of("bad-layer", 2),
                List.of("bad-layer", "EMPTY-OLD"),
                List.of("EMPTY-OLD"),
                List.of("TBD 待定开孔"),
                List.of(),
                Map.of(),
                "ezdxf",
                "1.4.4"
        );

        List<ReviewRule> rules = List.of(
                rule("LAYER_NAME_STANDARD", Severity.MEDIUM),
                rule("EMPTY_LAYER_CHECK", Severity.LOW),
                rule("TITLE_BLOCK_REQUIRED", Severity.HIGH),
                rule("VERSION_FORMAT", Severity.MEDIUM),
                rule("TEXT_PLACEHOLDER", Severity.MEDIUM),
                rule("ENTITY_DENSITY", Severity.HIGH)
        );

        List<ReviewIssue> issues = new RuleEngine().run("task_test", version, summary, List.of(entity), rules);

        assertThat(issues).extracting(issue -> issue.ruleCode)
                .contains("LAYER_NAME_STANDARD", "EMPTY_LAYER_CHECK", "TITLE_BLOCK_REQUIRED", "VERSION_FORMAT", "TEXT_PLACEHOLDER", "ENTITY_DENSITY");
        assertThat(issues).filteredOn(issue -> "LAYER_NAME_STANDARD".equals(issue.ruleCode) && "entity_test".equals(issue.entityRef))
                .hasSize(1);
        assertThat(issues).filteredOn(issue -> "TEXT_PLACEHOLDER".equals(issue.ruleCode)).singleElement()
                .satisfies(issue -> assertThat(issue.entityRef).isEqualTo("entity_test"));
    }

    @Test
    void ignoresDxfSystemLayersForLayerRules() {
        DrawingVersion version = new DrawingVersion();
        version.id = "version_system_layers";
        version.versionNo = "V1";

        WorkerSummary summary = new WorkerSummary(
                3,
                Map.of("LINE", 3),
                Map.of("S-HULL", 3),
                List.of("0", "Defpoints", "S-HULL"),
                List.of("0", "Defpoints"),
                List.of(),
                List.of("TITLE_BLOCK"),
                Map.of(),
                "ezdxf",
                "1.4.4"
        );

        List<ReviewRule> rules = List.of(
                rule("LAYER_NAME_STANDARD", Severity.MEDIUM),
                rule("EMPTY_LAYER_CHECK", Severity.LOW)
        );

        List<ReviewIssue> issues = new RuleEngine().run("task_system_layers", version, summary, List.of(), rules);

        assertThat(issues).isEmpty();
    }

    @Test
    void detectsTitleAttributeAndDimensionLayerRules() {
        DrawingVersion version = version("version_title_attr", "V1");
        ParsedEntity drawingNo = attribute("entity_drawing_no", "DRAWING_NO", "A22-001");
        ParsedEntity dimension = entity("entity_dimension", "DIMENSION", "S-HULL", "120.0", "", "{}");
        WorkerSummary summary = summary(
                6,
                Map.of("INSERT", 1, "ATTRIB", 1, "DIMENSION", 1, "LINE", 3),
                List.of("TITLE", "S-HULL", "DIM-MAIN"),
                List.of("TITLE_BLOCK")
        );

        List<ReviewIssue> issues = new RuleEngine().run(
                "task_title_attr",
                version,
                summary,
                List.of(drawingNo, dimension),
                List.of(
                        rule("TITLE_ATTRIBUTE_REQUIRED", Severity.HIGH),
                        rule("DIMENSION_LAYER_STANDARD", Severity.MEDIUM)
                )
        );

        assertThat(issues).extracting(issue -> issue.ruleCode)
                .containsExactlyInAnyOrder("TITLE_ATTRIBUTE_REQUIRED", "DIMENSION_LAYER_STANDARD");
        assertThat(issues).filteredOn(issue -> "DIMENSION_LAYER_STANDARD".equals(issue.ruleCode)).singleElement()
                .satisfies(issue -> {
                    assertThat(issue.layerName).isEqualTo("S-HULL");
                    assertThat(issue.entityRef).isEqualTo("entity_dimension");
                });
    }

    @Test
    void detectsTitleRevisionMismatch() {
        DrawingVersion version = version("version_revision", "V1");
        ParsedEntity drawingNo = attribute("entity_drawing_no", "DRAWING_NO", "A22-001");
        ParsedEntity revision = attribute("entity_revision", "REVISION", "V2");
        WorkerSummary summary = summary(
                6,
                Map.of("INSERT", 1, "ATTRIB", 2, "DIMENSION", 1, "LINE", 2),
                List.of("TITLE", "S-HULL", "DIM-MAIN"),
                List.of("TITLE_BLOCK")
        );

        List<ReviewIssue> issues = new RuleEngine().run(
                "task_revision",
                version,
                summary,
                List.of(drawingNo, revision),
                List.of(rule("VERSION_TITLE_CONSISTENCY", Severity.MEDIUM))
        );

        assertThat(issues).singleElement().satisfies(issue -> {
            assertThat(issue.ruleCode).isEqualTo("VERSION_TITLE_CONSISTENCY");
            assertThat(issue.entityRef).isEqualTo("entity_revision");
            assertThat(issue.layerName).isEqualTo("TITLE");
        });
    }

    @Test
    void requiresDimensionsForStructuredDrawing() {
        DrawingVersion version = version("version_missing_dimension", "V1");
        ParsedEntity drawingNo = attribute("entity_drawing_no", "DRAWING_NO", "A22-001");
        ParsedEntity revision = attribute("entity_revision", "REVISION", "V1");
        WorkerSummary summary = summary(
                5,
                Map.of("INSERT", 1, "ATTRIB", 2, "LINE", 2),
                List.of("TITLE", "S-HULL", "DIM-MAIN"),
                List.of("TITLE_BLOCK")
        );

        List<ReviewIssue> issues = new RuleEngine().run(
                "task_missing_dimension",
                version,
                summary,
                List.of(drawingNo, revision),
                List.of(rule("DIMENSION_REQUIRED", Severity.MEDIUM))
        );

        assertThat(issues).singleElement().satisfies(issue -> {
            assertThat(issue.ruleCode).isEqualTo("DIMENSION_REQUIRED");
            assertThat(issue.entityRef).isEmpty();
        });
    }

    @Test
    void acceptsCompleteTitleAndDimensionEvidence() {
        DrawingVersion version = version("version_clean", "V1");
        ParsedEntity drawingNo = attribute("entity_drawing_no", "DRAWING_NO", "A22-001");
        ParsedEntity revision = attribute("entity_revision", "REVISION", "V1");
        ParsedEntity dimension = entity("entity_dimension", "DIMENSION", "DIM-MAIN", "120.0", "", "{}");
        WorkerSummary summary = summary(
                6,
                Map.of("INSERT", 1, "ATTRIB", 2, "DIMENSION", 1, "LINE", 2),
                List.of("TITLE", "S-HULL", "DIM-MAIN"),
                List.of("TITLE_BLOCK")
        );

        List<ReviewIssue> issues = new RuleEngine().run(
                "task_clean",
                version,
                summary,
                List.of(drawingNo, revision, dimension),
                List.of(
                        rule("TITLE_ATTRIBUTE_REQUIRED", Severity.HIGH),
                        rule("VERSION_TITLE_CONSISTENCY", Severity.MEDIUM),
                        rule("DIMENSION_REQUIRED", Severity.MEDIUM),
                        rule("DIMENSION_LAYER_STANDARD", Severity.MEDIUM)
                )
        );

        assertThat(issues).isEmpty();
    }

    private DrawingVersion version(String id, String versionNo) {
        DrawingVersion version = new DrawingVersion();
        version.id = id;
        version.versionNo = versionNo;
        return version;
    }

    private ParsedEntity attribute(String id, String tag, String value) {
        return entity(id, "ATTRIB", "TITLE", value, "TITLE_BLOCK", "{\"geometry\":{\"tag\":\"" + tag + "\"}}");
    }

    private ParsedEntity entity(String id, String type, String layer, String text, String block, String rawJson) {
        ParsedEntity entity = new ParsedEntity();
        entity.id = id;
        entity.entityType = type;
        entity.layerName = layer;
        entity.textValue = text;
        entity.blockName = block;
        entity.rawJson = rawJson;
        return entity;
    }

    private WorkerSummary summary(int entityCount, Map<String, Integer> typeCounts, List<String> layers, List<String> blocks) {
        return new WorkerSummary(
                entityCount,
                typeCounts,
                Map.of(),
                layers,
                List.of(),
                List.of(),
                blocks,
                Map.of(),
                "ezdxf",
                "1.4.4"
        );
    }

    private ReviewRule rule(String code, Severity severity) {
        ReviewRule rule = new ReviewRule();
        rule.id = "rule_" + code;
        rule.code = code;
        rule.name = code;
        rule.description = code;
        rule.severity = severity;
        rule.enabled = true;
        return rule;
    }
}
