package com.shipcad.review.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shipcad.review.domain.AiExplanation;
import com.shipcad.review.domain.EvidenceType;
import com.shipcad.review.domain.IssueStatus;
import com.shipcad.review.domain.ReviewEvidence;
import com.shipcad.review.domain.ReviewIssue;
import com.shipcad.review.domain.Severity;
import java.util.List;
import org.junit.jupiter.api.Test;

class AiGatewayTest {
    private final AiGateway gateway = new AiGateway(new ObjectMapper());

    @Test
    void explainsIssueFromEvidenceChainOnly() {
        ReviewIssue issue = new ReviewIssue();
        issue.id = "issue_1";
        issue.ruleCode = "DIMENSION_REQUIRED";
        issue.title = "缺少尺寸标注";
        issue.description = "当前图纸未解析到尺寸标注。";
        issue.suggestion = "补充关键结构尺寸。";
        issue.severity = Severity.MEDIUM;
        issue.status = IssueStatus.OPEN;
        issue.evidences = List.of(
                evidence(EvidenceType.RULE_RESULT, "DIMENSION_REQUIRED", "Rule DIMENSION_REQUIRED generated this review issue.", "{}"),
                evidence(EvidenceType.CAD_SUMMARY, "version_1", "CAD parse summary supports this version-level issue.", "{}"),
                evidence(EvidenceType.KNOWLEDGE_CLAUSE, "BASIS_DIMENSION_EVIDENCE", "尺寸标注审查依据：结构图纸应保留关键尺寸标注。",
                        "{\"remediationHint\":\"补充关键结构尺寸，并复核 DIM-* 图层。\"}")
        );

        AiExplanation explanation = gateway.explain(issue);

        assertThat(explanation.model).isEqualTo("local-evidence-summarizer-v1");
        assertThat(explanation.summary).contains("DIMENSION_REQUIRED", "缺少尺寸标注");
        assertThat(explanation.reason).contains("CAD parse summary");
        assertThat(explanation.basis).contains("尺寸标注审查依据");
        assertThat(explanation.recommendation).contains("补充关键结构尺寸");
        assertThat(explanation.reviewFocus).contains("整改");
        assertThat(explanation.evidenceRefs).contains("KNOWLEDGE_CLAUSE:BASIS_DIMENSION_EVIDENCE");
    }

    private ReviewEvidence evidence(EvidenceType type, String sourceId, String summary, String payloadJson) {
        ReviewEvidence evidence = new ReviewEvidence();
        evidence.id = "evidence_" + type;
        evidence.evidenceType = type;
        evidence.sourceId = sourceId;
        evidence.sourceLabel = "test";
        evidence.summary = summary;
        evidence.payloadJson = payloadJson;
        evidence.confidence = 1.0;
        return evidence;
    }
}
