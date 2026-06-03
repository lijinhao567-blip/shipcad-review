package com.shipcad.review.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shipcad.review.domain.AiExplanation;
import com.shipcad.review.domain.Drawing;
import com.shipcad.review.domain.DrawingVersion;
import com.shipcad.review.domain.EvidenceType;
import com.shipcad.review.domain.IssueStatus;
import com.shipcad.review.domain.ParsedEntity;
import com.shipcad.review.domain.Project;
import com.shipcad.review.domain.ReviewEvidence;
import com.shipcad.review.domain.ReviewIssue;
import com.shipcad.review.domain.Severity;
import com.shipcad.review.dto.ApiDtos.WorkerSummary;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ReviewReportBuilderTest {
    private final ReviewReportBuilder builder = new ReviewReportBuilder(new ObjectMapper());

    @Test
    void buildsEvidenceAwareReport() {
        ReviewIssue issue = issue("issue_revision", "VERSION_TITLE_CONSISTENCY", Severity.MEDIUM, "entity_revision");
        issue.evidences.add(evidence("evidence_rule", issue, EvidenceType.RULE_RESULT, "Rule VERSION_TITLE_CONSISTENCY generated this review issue."));
        issue.evidences.add(evidence("evidence_entity", issue, EvidenceType.CAD_ENTITY, "CAD entity entity_revision supports this issue."));
        issue.evidences.add(evidence("evidence_clause", issue, EvidenceType.KNOWLEDGE_CLAUSE, "版次可追溯依据: 标题栏版次应与系统上传版次一致。"));
        issue.aiExplanation = explanation();
        ParsedEntity revision = entity(
                "entity_revision",
                "ATTRIB",
                "TITLE",
                "V2",
                "TITLE_BLOCK",
                "{\"geometry\":{\"tag\":\"REVISION\"}}"
        );
        ParsedEntity dimension = entity(
                "entity_dimension",
                "DIMENSION",
                "DIM-MAIN",
                "120.0",
                "",
                "{\"geometry\":{\"measurement\":120.0}}"
        );

        String report = builder.build(
                project(),
                drawing(),
                version(),
                summary(),
                List.of(issue),
                List.of(revision, dimension)
        );

        assertThat(report).contains("# Hull Section 审查报告");
        assertThat(report).contains("VERSION_TITLE_CONSISTENCY");
        assertThat(report).contains("entityRef=entity_revision");
        assertThat(report).contains("type=ATTRIB");
        assertThat(report).contains("tag=REVISION");
        assertThat(report).contains("text=V2");
        assertThat(report).contains("MEDIUM 1");
        assertThat(report).contains("Evidence chain");
        assertThat(report).contains("AI辅助解释");
        assertThat(report).contains("规则 VERSION_TITLE_CONSISTENCY 命中");
        assertThat(report).contains("RULE_RESULT");
        assertThat(report).contains("KNOWLEDGE_CLAUSE");
        assertThat(report).contains("结构化证据 3 条");
        assertThat(report).contains("实体类型：ATTRIB=1, DIMENSION=1, LINE=2");
    }

    @Test
    void buildsCleanReportWhenNoIssuesExist() {
        String report = builder.build(
                project(),
                drawing(),
                version(),
                summary(),
                List.of(),
                List.of()
        );

        assertThat(report).contains("未发现当前规则集命中的问题");
        assertThat(report).contains("当前规则集未生成审查问题");
    }

    private Project project() {
        Project project = new Project();
        project.name = "A22 Test Project";
        project.shipNo = "SHIP-A22";
        return project;
    }

    private Drawing drawing() {
        Drawing drawing = new Drawing();
        drawing.drawingNo = "A22-001";
        drawing.title = "Hull Section";
        drawing.discipline = "Hull Structure";
        return drawing;
    }

    private DrawingVersion version() {
        DrawingVersion version = new DrawingVersion();
        version.id = "version_1";
        version.versionNo = "V1";
        version.fileName = "sample.dxf";
        return version;
    }

    private WorkerSummary summary() {
        return new WorkerSummary(
                4,
                Map.of("LINE", 2, "ATTRIB", 1, "DIMENSION", 1),
                Map.of("TITLE", 1, "DIM-MAIN", 1, "S-HULL", 2),
                List.of("TITLE", "S-HULL", "DIM-MAIN"),
                List.of(),
                List.of("V2"),
                List.of("TITLE_BLOCK"),
                Map.of("minX", 0.0, "minY", -8.0, "maxX", 120.0, "maxY", 60.0),
                "ezdxf",
                "1.4.4"
        );
    }

    private ReviewIssue issue(String id, String ruleCode, Severity severity, String entityRef) {
        ReviewIssue issue = new ReviewIssue();
        issue.id = id;
        issue.versionId = "version_1";
        issue.ruleCode = ruleCode;
        issue.title = "标题栏版次与上传版次不一致";
        issue.description = "标题栏 REVISION 为 V2，但系统上传版次为 V1。";
        issue.severity = severity;
        issue.status = IssueStatus.OPEN;
        issue.layerName = "TITLE";
        issue.entityRef = entityRef;
        issue.suggestion = "建议统一标题栏版次与系统版本记录。";
        return issue;
    }

    private AiExplanation explanation() {
        AiExplanation explanation = new AiExplanation();
        explanation.model = "local-evidence-summarizer-v1";
        explanation.summary = "规则 VERSION_TITLE_CONSISTENCY 命中：标题栏版次与上传版次不一致";
        explanation.reason = "规则命中，CAD 证据指向标题栏 REVISION 属性。";
        explanation.basis = "依据条款：版次可追溯依据。";
        explanation.recommendation = "统一标题栏版次与系统版本记录。";
        explanation.reviewFocus = "建议在本轮整改中处理。";
        return explanation;
    }

    private ReviewEvidence evidence(String id, ReviewIssue issue, EvidenceType type, String summary) {
        ReviewEvidence evidence = new ReviewEvidence();
        evidence.id = id;
        evidence.issueId = issue.id;
        evidence.taskId = issue.taskId;
        evidence.versionId = issue.versionId;
        evidence.ruleCode = issue.ruleCode;
        evidence.evidenceType = type;
        evidence.sourceId = type.name();
        evidence.sourceLabel = "test";
        evidence.summary = summary;
        evidence.payloadJson = "{}";
        evidence.confidence = 1.0;
        return evidence;
    }

    private ParsedEntity entity(String id, String type, String layer, String text, String block, String rawJson) {
        ParsedEntity entity = new ParsedEntity();
        entity.id = id;
        entity.entityType = type;
        entity.layerName = layer;
        entity.textValue = text;
        entity.blockName = block;
        entity.x = 8.0;
        entity.y = 5.0;
        entity.rawJson = rawJson;
        return entity;
    }
}
