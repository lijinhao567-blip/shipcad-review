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
