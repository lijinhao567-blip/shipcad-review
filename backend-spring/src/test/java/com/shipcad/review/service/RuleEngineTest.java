package com.shipcad.review.service;

import com.shipcad.review.domain.DrawingVersion;
import com.shipcad.review.domain.EvidenceLocation;
import com.shipcad.review.domain.EvidenceType;
import com.shipcad.review.domain.KnowledgeClause;
import com.shipcad.review.domain.ParsedEntity;
import com.shipcad.review.domain.ReviewEvidence;
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
        assertThat(issues).allSatisfy(issue -> {
            assertThat(issue.evidences).isNotEmpty();
            assertThat(issue.evidences).extracting(evidence -> evidence.evidenceType)
                    .contains(EvidenceType.RULE_RESULT);
        });
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
                    assertThat(issue.evidences).extracting(evidence -> evidence.evidenceType)
                            .contains(EvidenceType.CAD_ENTITY, EvidenceType.RULE_RESULT);
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
            assertThat(issue.evidences).extracting(evidence -> evidence.evidenceType)
                    .contains(EvidenceType.CAD_SUMMARY, EvidenceType.RULE_RESULT);
        });
    }

    @Test
    void attachesKnowledgeClauseEvidenceWhenRuleIsBound() {
        DrawingVersion version = version("version_missing_dimension_basis", "V1");
        ParsedEntity drawingNo = attribute("entity_drawing_no", "DRAWING_NO", "A22-001");
        ParsedEntity revision = attribute("entity_revision", "REVISION", "V1");
        WorkerSummary summary = summary(
                5,
                Map.of("INSERT", 1, "ATTRIB", 2, "LINE", 2),
                List.of("TITLE", "S-HULL", "DIM-MAIN"),
                List.of("TITLE_BLOCK")
        );
        ReviewRule rule = rule("DIMENSION_REQUIRED", Severity.MEDIUM);
        rule.knowledgeClauseCode = "BASIS_DIMENSION_EVIDENCE";
        KnowledgeClause clause = clause("BASIS_DIMENSION_EVIDENCE", "尺寸标注审查依据");

        List<ReviewIssue> issues = new RuleEngine().run(
                "task_missing_dimension_basis",
                version,
                summary,
                List.of(drawingNo, revision),
                List.of(rule),
                List.of(clause)
        );

        assertThat(issues).singleElement().satisfies(issue -> {
            assertThat(issue.ruleCode).isEqualTo("DIMENSION_REQUIRED");
            assertThat(issue.evidences).extracting(evidence -> evidence.evidenceType)
                    .contains(EvidenceType.KNOWLEDGE_CLAUSE);
            assertThat(issue.evidences).filteredOn(evidence -> evidence.evidenceType == EvidenceType.KNOWLEDGE_CLAUSE)
                    .singleElement()
                    .satisfies(evidence -> {
                        assertThat(evidence.sourceId).isEqualTo("BASIS_DIMENSION_EVIDENCE");
                        assertThat(evidence.summary).contains("尺寸标注审查依据");
                    });
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

    @Test
    void consumesOcrPlaceholderEvidence() {
        DrawingVersion version = version("version_ocr_placeholder", "V1");
        ReviewRule rule = rule("OCR_PLACEHOLDER_TEXT", Severity.MEDIUM);
        rule.knowledgeClauseCode = "BASIS_OCR_TEXT_EVIDENCE";
        ReviewEvidence ocr = evidence(
                "evidence_ocr_tbd",
                EvidenceType.OCR_TEXT,
                "ocr:text#0",
                "ocr_worker.tesseract",
                "{\"text\":\"TBD bracket detail\",\"confidence\":0.91,\"xyxy\":[10,20,80,34],\"engine\":\"tesseract\"}"
        );
        ocr.location = EvidenceLocations.rasterBox(
                ocr.sourceId,
                List.of(10.0, 20.0, 80.0, 34.0),
                800,
                600,
                "rendered-version-image",
                Map.of("minX", 0.0, "minY", 0.0, "maxX", 160.0, "maxY", 120.0)
        );

        List<ReviewIssue> issues = new RuleEngine().run(
                "task_ocr_placeholder",
                version,
                summary(5, Map.of("LINE", 5), List.of("S-HULL"), List.of("TITLE_BLOCK")),
                List.of(),
                List.of(rule),
                List.of(clause("BASIS_OCR_TEXT_EVIDENCE", "OCR文字证据审查依据")),
                List.of(ocr)
        );

        assertThat(issues).singleElement().satisfies(issue -> {
            assertThat(issue.ruleCode).isEqualTo("OCR_PLACEHOLDER_TEXT");
            assertThat(issue.description).contains("TBD bracket detail");
            assertThat(issue.evidences).extracting(evidence -> evidence.evidenceType)
                    .contains(EvidenceType.RULE_RESULT, EvidenceType.OCR_TEXT, EvidenceType.KNOWLEDGE_CLAUSE);
            assertThat(issue.evidences).filteredOn(evidence -> evidence.evidenceType == EvidenceType.OCR_TEXT)
                    .singleElement()
                    .satisfies(evidence -> {
                        assertThat(evidence.sourceId).isEqualTo("ocr:text#0");
                        assertThat(evidence.payloadJson).contains("\"sourceEvidenceId\":\"evidence_ocr_tbd\"");
                        assertThat(evidence.payloadJson).contains("\"text\":\"TBD bracket detail\"");
                        assertThat(evidence.location).isEqualTo(ocr.location);
                        assertThat(evidence.location.transform().targetSpace()).isEqualTo(EvidenceLocation.SPACE_CAD_MODEL);
                        EvidenceLocation.Bounds mapped = EvidenceLocations.mapRasterBoundsToCad(evidence.location);
                        assertThat(mapped.minX()).isEqualTo(2.0);
                        assertThat(mapped.minY()).isEqualTo(113.2);
                        assertThat(mapped.maxX()).isEqualTo(16.0);
                        assertThat(mapped.maxY()).isEqualTo(116.0);
                    });
        });
    }

    @Test
    void crossChecksYoloTitleBlockAgainstCadParsing() {
        DrawingVersion version = version("version_yolo_title", "V1");
        ReviewEvidence titleBlock = evidence(
                "evidence_yolo_title",
                EvidenceType.YOLO_SYMBOL,
                "symbol:title_block#0",
                "vision_worker.yolov8",
                "{\"className\":\"title_block\",\"confidence\":0.88,\"xyxy\":[100,200,600,380],\"engine\":\"ultralytics-yolov8\"}"
        );
        titleBlock.location = EvidenceLocations.rasterBox(
                titleBlock.sourceId,
                List.of(100.0, 200.0, 600.0, 380.0),
                1000,
                500,
                "rendered-version-image",
                Map.of("minX", 0.0, "minY", 0.0, "maxX", 200.0, "maxY", 100.0)
        );

        List<ReviewIssue> issues = new RuleEngine().run(
                "task_yolo_title",
                version,
                summary(5, Map.of("LINE", 5), List.of("S-HULL"), List.of()),
                List.of(),
                List.of(
                        rule("TITLE_BLOCK_REQUIRED", Severity.HIGH),
                        rule("YOLO_TITLE_BLOCK_CAD_MISSING", Severity.MEDIUM)
                ),
                List.of(),
                List.of(titleBlock)
        );

        assertThat(issues).extracting(issue -> issue.ruleCode)
                .containsExactly("YOLO_TITLE_BLOCK_CAD_MISSING");
        assertThat(issues).singleElement().satisfies(issue -> {
            assertThat(issue.description).contains("title_block");
            assertThat(issue.evidences).extracting(evidence -> evidence.evidenceType)
                    .contains(EvidenceType.RULE_RESULT, EvidenceType.YOLO_SYMBOL);
            assertThat(issue.evidences).filteredOn(evidence -> evidence.evidenceType == EvidenceType.YOLO_SYMBOL)
                    .singleElement()
                    .satisfies(evidence -> {
                        assertThat(evidence.payloadJson).contains("\"sourceEvidenceId\":\"evidence_yolo_title\"");
                        assertThat(evidence.location).isEqualTo(titleBlock.location);
                        assertThat(EvidenceLocations.mapRasterBoundsToCad(evidence.location))
                                .isEqualTo(new EvidenceLocation.Bounds(20.0, 24.0, 120.0, 60.0));
                    });
        });
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

    private ReviewEvidence evidence(String id, EvidenceType type, String sourceId, String sourceLabel, String payloadJson) {
        ReviewEvidence evidence = new ReviewEvidence();
        evidence.id = id;
        evidence.issueId = null;
        evidence.taskId = null;
        evidence.versionId = "version_test";
        evidence.ruleCode = type == EvidenceType.OCR_TEXT ? "OCR_RECOGNITION" : "VISION_DETECTION";
        evidence.evidenceType = type;
        evidence.sourceId = sourceId;
        evidence.sourceLabel = sourceLabel;
        evidence.summary = sourceLabel + " evidence";
        evidence.payloadJson = payloadJson;
        evidence.confidence = 0.9;
        evidence.createdAt = Ids.now();
        return evidence;
    }

    private KnowledgeClause clause(String code, String title) {
        KnowledgeClause clause = new KnowledgeClause();
        clause.id = "clause_" + code;
        clause.code = code;
        clause.title = title;
        clause.content = "结构图纸应保留关键尺寸标注，且尺寸实体应放置在约定的尺寸图层。";
        clause.source = "TEST_BASIS";
        clause.tags = "dimension,review";
        clause.remediationHint = "补充关键结构尺寸。";
        return clause;
    }
}
