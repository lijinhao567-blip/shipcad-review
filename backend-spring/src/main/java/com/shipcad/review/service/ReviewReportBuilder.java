package com.shipcad.review.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shipcad.review.domain.Drawing;
import com.shipcad.review.domain.DrawingVersion;
import com.shipcad.review.domain.ParsedEntity;
import com.shipcad.review.domain.Project;
import com.shipcad.review.domain.ReviewEvidence;
import com.shipcad.review.domain.ReviewIssue;
import com.shipcad.review.domain.Severity;
import com.shipcad.review.dto.ApiDtos.WorkerSummary;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class ReviewReportBuilder {
    private final ObjectMapper mapper;

    public ReviewReportBuilder(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public String build(
            Project project,
            Drawing drawing,
            DrawingVersion version,
            WorkerSummary summary,
            List<ReviewIssue> issues,
            List<ParsedEntity> entities
    ) {
        Map<String, ParsedEntity> entityById = entities.stream()
                .filter(entity -> entity.id != null)
                .collect(Collectors.toMap(entity -> entity.id, entity -> entity, (left, right) -> left));

        StringBuilder builder = new StringBuilder();
        builder.append("# ").append(value(drawing.title)).append(" 审查报告\n\n")
                .append("## 基本信息\n\n")
                .append("| 项目 | 图纸 | 版本 |\n")
                .append("|---|---|---|\n")
                .append("| ").append(cell(project.name)).append(" / ").append(cell(project.shipNo)).append(" | ")
                .append(cell(drawing.drawingNo)).append(" / ").append(cell(drawing.title)).append(" / ").append(cell(drawing.discipline)).append(" | ")
                .append(cell(version.versionNo)).append(" / ").append(cell(version.fileName)).append(" |\n\n")
                .append("## AI辅助摘要\n\n")
                .append(summaryText(issues, entityById))
                .append("\n\n")
                .append("## 解析证据摘要\n\n")
                .append("- 解析器：").append(value(summary.parser())).append(" / ezdxf ").append(value(summary.ezdxfVersion())).append("\n")
                .append("- 实体总数：").append(summary.entityCount()).append("\n")
                .append("- 实体类型：").append(inlineCounts(summary.typeCounts())).append("\n")
                .append("- 图层：").append(inlineList(summary.layers())).append("\n")
                .append("- 标题栏/块参照：").append(inlineList(summary.blocks())).append("\n")
                .append("- 图纸边界：").append(bounds(summary.bounds())).append("\n\n")
                .append("## 问题清单\n\n")
                .append("| 序号 | 规则 | 严重级别 | 状态 | 定位证据 | 问题说明 | 整改建议 |\n")
                .append("|---:|---|---|---|---|---|---|\n");

        List<ReviewIssue> sortedIssues = sortedIssues(issues);
        for (int i = 0; i < sortedIssues.size(); i++) {
            ReviewIssue issue = sortedIssues.get(i);
            builder.append("| ").append(i + 1)
                    .append(" | ").append(cell(issue.ruleCode))
                    .append(" | ").append(cell(issue.severity))
                    .append(" | ").append(cell(issue.status))
                    .append(" | ").append(cell(evidenceSummary(issue, entityById)))
                    .append(" | ").append(cell(issue.description))
                    .append(" | ").append(cell(issue.suggestion))
                    .append(" |\n");
        }

        builder.append("\n## 问题证据详情\n\n");
        if (sortedIssues.isEmpty()) {
            builder.append("当前规则集未生成审查问题。仍建议由审图人员结合专业规范进行最终复核。\n");
        }
        for (int i = 0; i < sortedIssues.size(); i++) {
            ReviewIssue issue = sortedIssues.get(i);
            ParsedEntity entity = entityById.get(issue.entityRef);
            builder.append("### ").append(i + 1).append(". ").append(value(issue.title)).append("\n\n")
                    .append("- 规则：").append(value(issue.ruleCode)).append("\n")
                    .append("- 严重级别：").append(value(issue.severity)).append("\n")
                    .append("- 状态：").append(value(issue.status)).append("\n")
                    .append("- 定位：").append(evidenceSummary(issue, entityById)).append("\n")
                    .append("- 说明：").append(value(issue.description)).append("\n")
                    .append("- 建议：").append(value(issue.suggestion)).append("\n");
            if (entity != null) {
                builder.append("- CAD实体证据：").append(entityDetail(entity)).append("\n");
            }
            if (issue.aiExplanation != null) {
                builder.append("- AI辅助解释：").append(value(issue.aiExplanation.summary)).append("\n")
                        .append("- AI证据原因：").append(value(issue.aiExplanation.reason)).append("\n")
                        .append("- AI依据说明：").append(value(issue.aiExplanation.basis)).append("\n")
                        .append("- AI整改建议：").append(value(issue.aiExplanation.recommendation)).append("\n")
                        .append("- AI复核关注：").append(value(issue.aiExplanation.reviewFocus)).append("\n");
            }
            if (issue.evidences != null && !issue.evidences.isEmpty()) {
                builder.append("- Evidence chain: ").append(evidenceChain(issue)).append("\n");
            }
            builder.append("\n");
        }

        builder.append("## 生成说明\n\n")
                .append("本报告由确定性规则和可追溯证据生成。AI辅助摘要只负责整理审查结果，不替代审图专家的最终判断。\n");
        return builder.toString();
    }

    private String summaryText(List<ReviewIssue> issues, Map<String, ParsedEntity> entityById) {
        if (issues.isEmpty()) {
            return "本次审查未发现当前规则集命中的问题。系统已完成解析证据汇总，可进入人工抽检或后续规则扩展。";
        }
        Map<Severity, Long> bySeverity = new EnumMap<>(Severity.class);
        for (ReviewIssue issue : issues) {
            Severity severity = issue.severity == null ? Severity.MEDIUM : issue.severity;
            bySeverity.put(severity, bySeverity.getOrDefault(severity, 0L) + 1);
        }
        long entityLinked = issues.stream()
                .filter(issue -> issue.entityRef != null && entityById.containsKey(issue.entityRef))
                .count();
        long layerLinked = issues.stream()
                .filter(issue -> (issue.entityRef == null || issue.entityRef.isBlank()) && issue.layerName != null && !issue.layerName.isBlank())
                .count();
        long evidenceCount = issues.stream()
                .mapToLong(issue -> issue.evidences == null ? 0 : issue.evidences.size())
                .sum();
        return "本次审查共发现 " + issues.size() + " 个规则命中问题，其中 "
                + severityText(bySeverity)
                + "。已关联 CAD 实体证据 " + entityLinked + " 条，图层级证据 " + layerLinked
                + " 条，结构化证据 " + evidenceCount + " 条；建议优先处理 HIGH 和 MEDIUM 问题。";
    }

    private String severityText(Map<Severity, Long> counts) {
        return List.of(Severity.HIGH, Severity.MEDIUM, Severity.LOW, Severity.INFO).stream()
                .map(severity -> severity + " " + counts.getOrDefault(severity, 0L))
                .collect(Collectors.joining("，"));
    }

    private List<ReviewIssue> sortedIssues(List<ReviewIssue> issues) {
        return issues.stream()
                .sorted(Comparator
                        .comparing((ReviewIssue issue) -> severityRank(issue.severity))
                        .thenComparing(issue -> value(issue.ruleCode))
                        .thenComparing(issue -> value(issue.id)))
                .toList();
    }

    private int severityRank(Severity severity) {
        if (severity == Severity.HIGH) {
            return 0;
        }
        if (severity == Severity.MEDIUM) {
            return 1;
        }
        if (severity == Severity.LOW) {
            return 2;
        }
        return 3;
    }

    private String evidenceSummary(ReviewIssue issue, Map<String, ParsedEntity> entityById) {
        if (issue.entityRef != null && !issue.entityRef.isBlank()) {
            ParsedEntity entity = entityById.get(issue.entityRef);
            if (entity == null) {
                return "entityRef=" + issue.entityRef + "（当前解析实体中未找到）";
            }
            return entityDetail(entity);
        }
        if (issue.layerName != null && !issue.layerName.isBlank()) {
            return "layer=" + issue.layerName;
        }
        return "version=" + issue.versionId;
    }

    private String entityDetail(ParsedEntity entity) {
        StringBuilder builder = new StringBuilder();
        builder.append("entityRef=").append(value(entity.id))
                .append("; type=").append(value(entity.entityType))
                .append("; layer=").append(value(entity.layerName));
        if (entity.blockName != null && !entity.blockName.isBlank()) {
            builder.append("; block=").append(entity.blockName);
        }
        if (entity.textValue != null && !entity.textValue.isBlank()) {
            builder.append("; text=").append(shorten(entity.textValue, 80));
        }
        if (entity.x != null && entity.y != null) {
            builder.append("; point=(").append(format(entity.x)).append(", ").append(format(entity.y)).append(")");
        }
        Map<String, Object> geometry = geometry(entity);
        appendGeometryValue(builder, geometry, "tag");
        appendGeometryValue(builder, geometry, "measurement");
        return builder.toString();
    }

    private String evidenceChain(ReviewIssue issue) {
        return issue.evidences.stream()
                .map(this::evidenceSummary)
                .collect(Collectors.joining(" | "));
    }

    private String evidenceSummary(ReviewEvidence evidence) {
        return value(evidence.evidenceType) + ": " + shorten(value(evidence.summary), 120);
    }

    private void appendGeometryValue(StringBuilder builder, Map<String, Object> geometry, String key) {
        Object value = geometry.get(key);
        if (value != null && !value.toString().isBlank()) {
            builder.append("; ").append(key).append("=").append(shorten(value.toString(), 60));
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> geometry(ParsedEntity entity) {
        if (entity.rawJson == null || entity.rawJson.isBlank()) {
            return Map.of();
        }
        try {
            Map<String, Object> raw = mapper.readValue(entity.rawJson, Map.class);
            Object geometry = raw.get("geometry");
            return geometry instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
        } catch (JsonProcessingException ignored) {
            return Map.of();
        }
    }

    private String inlineCounts(Map<String, Integer> counts) {
        if (counts == null || counts.isEmpty()) {
            return "-";
        }
        return counts.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining(", "));
    }

    private String inlineList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "-";
        }
        return values.stream()
                .filter(Objects::nonNull)
                .limit(30)
                .collect(Collectors.joining(", "));
    }

    private String bounds(Map<String, Double> bounds) {
        if (bounds == null || bounds.isEmpty()) {
            return "-";
        }
        return "minX=" + format(bounds.get("minX"))
                + ", minY=" + format(bounds.get("minY"))
                + ", maxX=" + format(bounds.get("maxX"))
                + ", maxY=" + format(bounds.get("maxY"));
    }

    private String format(Double value) {
        if (value == null) {
            return "-";
        }
        return String.format(java.util.Locale.ROOT, "%.2f", value);
    }

    private String cell(Object value) {
        return value(value).replace("|", "\\|").replace("\r\n", "<br>").replace("\n", "<br>").replace("\r", "<br>");
    }

    private String value(Object value) {
        if (value == null) {
            return "-";
        }
        String text = value.toString();
        return text.isBlank() ? "-" : text;
    }

    private String shorten(String value, int maxLength) {
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength - 3) + "...";
    }
}
