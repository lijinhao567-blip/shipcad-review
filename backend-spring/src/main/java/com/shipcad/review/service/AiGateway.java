package com.shipcad.review.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shipcad.review.domain.AiExplanation;
import com.shipcad.review.domain.EvidenceType;
import com.shipcad.review.domain.ReviewEvidence;
import com.shipcad.review.domain.ReviewIssue;
import com.shipcad.review.domain.Severity;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;

@Service
public class AiGateway {
    private static final String MODEL_NAME = "local-evidence-summarizer-v1";
    private final ObjectMapper mapper;

    public AiGateway(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public AiExplanation explain(ReviewIssue issue) {
        List<ReviewEvidence> evidences = issue.evidences == null ? List.of() : issue.evidences;
        ReviewEvidence cadEvidence = firstEvidence(evidences, EvidenceType.CAD_ENTITY, EvidenceType.CAD_LAYER, EvidenceType.CAD_SUMMARY);
        ReviewEvidence ruleEvidence = firstEvidence(evidences, EvidenceType.RULE_RESULT);
        ReviewEvidence clauseEvidence = firstEvidence(evidences, EvidenceType.KNOWLEDGE_CLAUSE);

        AiExplanation explanation = new AiExplanation();
        explanation.model = MODEL_NAME;
        explanation.summary = "规则 " + value(issue.ruleCode) + " 命中：" + value(issue.title);
        explanation.reason = reasonText(issue, cadEvidence, ruleEvidence);
        explanation.basis = clauseEvidence == null
                ? "当前规则尚未绑定依据条款，解释仅基于规则命中和 CAD 解析证据。"
                : "依据条款：" + value(clauseEvidence.summary);
        explanation.recommendation = recommendationText(issue, clauseEvidence);
        explanation.reviewFocus = reviewFocus(issue);
        explanation.evidenceRefs = evidences.stream()
                .map(evidence -> value(evidence.evidenceType) + ":" + value(evidence.sourceId))
                .filter(ref -> !ref.isBlank())
                .toList();
        return explanation;
    }

    private String reasonText(ReviewIssue issue, ReviewEvidence cadEvidence, ReviewEvidence ruleEvidence) {
        String rule = ruleEvidence == null ? "规则引擎生成了该问题。" : value(ruleEvidence.summary);
        if (cadEvidence == null) {
            return rule + " 当前未找到可附加的 CAD 定位证据，需结合任务上下文复核。";
        }
        return rule + " CAD 证据显示：" + value(cadEvidence.summary);
    }

    private String recommendationText(ReviewIssue issue, ReviewEvidence clauseEvidence) {
        String directSuggestion = value(issue.suggestion);
        String remediationHint = remediationHint(clauseEvidence);
        if (!directSuggestion.isBlank() && !remediationHint.isBlank() && !directSuggestion.equals(remediationHint)) {
            return directSuggestion + " 依据条款补充建议：" + remediationHint;
        }
        if (!directSuggestion.isBlank()) {
            return directSuggestion;
        }
        if (!remediationHint.isBlank()) {
            return remediationHint;
        }
        return "请审图人员结合证据链进行人工复核，并补充整改意见。";
    }

    private String reviewFocus(ReviewIssue issue) {
        Severity severity = issue.severity == null ? Severity.MEDIUM : issue.severity;
        return switch (severity) {
            case HIGH -> "优先复核。该问题可能影响图纸可追溯性、审查完整性或上传文件有效性。";
            case MEDIUM -> "建议在本轮整改中处理，并复核相关图层、标题栏、尺寸或文字证据。";
            case LOW -> "可作为清理项处理，重点确认是否为历史保留或设计意图。";
            case INFO -> "作为提示项记录，必要时由审图人员确认。";
        };
    }

    private ReviewEvidence firstEvidence(List<ReviewEvidence> evidences, EvidenceType... types) {
        for (EvidenceType type : types) {
            ReviewEvidence match = evidences.stream()
                    .filter(evidence -> evidence.evidenceType == type)
                    .findFirst()
                    .orElse(null);
            if (match != null) {
                return match;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private String remediationHint(ReviewEvidence evidence) {
        if (evidence == null || evidence.payloadJson == null || evidence.payloadJson.isBlank()) {
            return "";
        }
        try {
            Map<String, Object> payload = mapper.readValue(evidence.payloadJson, Map.class);
            Object value = payload.get("remediationHint");
            return value == null ? "" : value.toString();
        } catch (JsonProcessingException ignored) {
            return "";
        }
    }

    private String value(Object value) {
        if (value == null) {
            return "";
        }
        String text = Objects.toString(value, "");
        return text.isBlank() ? "" : text;
    }
}
