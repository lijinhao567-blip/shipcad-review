package com.shipcad.review.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shipcad.review.domain.DrawingVersion;
import com.shipcad.review.domain.IssueStatus;
import com.shipcad.review.domain.ParsedEntity;
import com.shipcad.review.domain.ReviewIssue;
import com.shipcad.review.domain.ReviewRule;
import com.shipcad.review.domain.Severity;
import com.shipcad.review.dto.ApiDtos.WorkerSummary;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.jeasy.rules.annotation.Action;
import org.jeasy.rules.annotation.Condition;
import org.jeasy.rules.annotation.Rule;
import org.jeasy.rules.api.Facts;
import org.jeasy.rules.api.Rules;
import org.jeasy.rules.api.RulesEngine;
import org.jeasy.rules.core.DefaultRulesEngine;
import org.springframework.stereotype.Service;

@Service
public class RuleEngine {
    private static final List<String> ALLOWED_LAYER_PREFIXES = List.of("S-", "P-", "E-", "H-", "M-", "A-", "DIM-", "TEXT-", "TITLE", "0");
    private static final Set<String> SYSTEM_LAYERS = Set.of("0", "Defpoints");
    private static final List<String> REQUIRED_TITLE_ATTRIBUTES = List.of("DRAWING_NO", "REVISION");
    private static final Pattern VERSION_PATTERN = Pattern.compile("^(V\\d+|[A-Z]|R\\d+)$");
    private static final List<String> PLACEHOLDERS = List.of("TBD", "TODO", "XXX", "待定", "未定");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public List<ReviewIssue> run(String taskId, DrawingVersion version, WorkerSummary summary, List<ParsedEntity> entities, List<ReviewRule> rules) {
        ReviewContext context = new ReviewContext(
                taskId,
                version,
                summary,
                entities,
                rules.stream().filter(rule -> rule.enabled).collect(Collectors.toMap(rule -> rule.code, rule -> rule)),
                new ArrayList<>()
        );
        Facts facts = new Facts();
        facts.put("context", context);

        Rules easyRules = new Rules();
        easyRules.register(new LayerNameRule());
        easyRules.register(new EmptyLayerRule());
        easyRules.register(new TitleBlockRule());
        easyRules.register(new VersionFormatRule());
        easyRules.register(new PlaceholderTextRule());
        easyRules.register(new EntityDensityRule());
        easyRules.register(new TitleAttributeRule());
        easyRules.register(new VersionTitleConsistencyRule());
        easyRules.register(new DimensionRequiredRule());
        easyRules.register(new DimensionLayerRule());

        RulesEngine engine = new DefaultRulesEngine();
        engine.fire(easyRules, facts);
        return context.issues();
    }

    private record ReviewContext(
            String taskId,
            DrawingVersion version,
            WorkerSummary summary,
            List<ParsedEntity> entities,
            Map<String, ReviewRule> enabledRules,
            List<ReviewIssue> issues
    ) {
    }

    private abstract static class BaseReviewRule {
        protected ReviewContext context(Facts facts) {
            return facts.get("context");
        }

        protected boolean enabled(Facts facts, String code) {
            return context(facts).enabledRules().containsKey(code);
        }

        protected List<String> safe(List<String> values) {
            return values == null ? List.of() : values;
        }

        protected ParsedEntity firstEntityOnLayer(Facts facts, String layer) {
            return context(facts).entities().stream()
                    .filter(entity -> layer != null && layer.equals(entity.layerName))
                    .findFirst()
                    .orElse(null);
        }

        protected boolean hasTitleBlock(Facts facts) {
            String blocks = String.join(" ", safe(context(facts).summary().blocks()));
            return blocks.toUpperCase(Locale.ROOT).contains("TITLE");
        }

        protected boolean isType(ParsedEntity entity, String type) {
            return type.equalsIgnoreCase(entity.entityType == null ? "" : entity.entityType);
        }

        protected int typeCount(Facts facts, String type) {
            Map<String, Integer> typeCounts = context(facts).summary().typeCounts();
            return typeCounts == null ? 0 : typeCounts.getOrDefault(type, 0);
        }

        protected List<ParsedEntity> titleAttributes(Facts facts) {
            return context(facts).entities().stream()
                    .filter(entity -> isType(entity, "ATTRIB"))
                    .filter(entity -> containsIgnoreCase(entity.blockName, "TITLE"))
                    .toList();
        }

        protected Map<String, ParsedEntity> titleAttributesByTag(Facts facts) {
            return titleAttributes(facts).stream()
                    .filter(entity -> !attributeTag(entity).isBlank())
                    .collect(Collectors.toMap(this::attributeTag, entity -> entity, (left, right) -> left));
        }

        protected String attributeTag(ParsedEntity entity) {
            return geometryString(entity, "tag").toUpperCase(Locale.ROOT);
        }

        protected String normalizeVersion(String value) {
            return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        }

        protected boolean validVersion(String value) {
            return VERSION_PATTERN.matcher(normalizeVersion(value)).matches();
        }

        private boolean containsIgnoreCase(String value, String part) {
            return value != null && value.toUpperCase(Locale.ROOT).contains(part.toUpperCase(Locale.ROOT));
        }

        @SuppressWarnings("unchecked")
        private String geometryString(ParsedEntity entity, String key) {
            if (entity.rawJson == null || entity.rawJson.isBlank()) {
                return "";
            }
            try {
                Map<String, Object> raw = MAPPER.readValue(entity.rawJson, Map.class);
                Object geometry = raw.get("geometry");
                if (geometry instanceof Map<?, ?> geometryMap) {
                    Object value = geometryMap.get(key);
                    return value == null ? "" : value.toString();
                }
            } catch (JsonProcessingException ignored) {
                return "";
            }
            return "";
        }

        protected void add(Facts facts, String code, String title, String description, String layer, String entityRef, String suggestion) {
            ReviewContext context = context(facts);
            ReviewRule rule = context.enabledRules().get(code);
            if (rule == null) {
                return;
            }
            ReviewIssue issue = new ReviewIssue();
            issue.id = Ids.next("issue");
            issue.taskId = context.taskId();
            issue.versionId = context.version().id;
            issue.ruleCode = code;
            issue.title = title;
            issue.description = description;
            issue.severity = rule.severity == null ? Severity.MEDIUM : rule.severity;
            issue.status = IssueStatus.OPEN;
            issue.layerName = layer;
            issue.entityRef = entityRef;
            issue.suggestion = suggestion;
            issue.createdAt = Ids.now();
            issue.updatedAt = issue.createdAt;
            issue.assignee = "";
            context.issues().add(issue);
        }
    }

    @Rule(name = "图层命名规范检查", priority = 1)
    public static class LayerNameRule extends BaseReviewRule {
        @Condition
        public boolean when(Facts facts) {
            return enabled(facts, "LAYER_NAME_STANDARD") && safe(context(facts).summary().layers()).stream().anyMatch(this::invalidLayer);
        }

        @Action
        public void then(Facts facts) {
            safe(context(facts).summary().layers()).stream()
                    .filter(this::invalidLayer)
                    .forEach(layer -> {
                        ParsedEntity entity = firstEntityOnLayer(facts, layer);
                        add(facts, "LAYER_NAME_STANDARD", "图层命名不规范：" + layer,
                                "图层 " + layer + " 未使用推荐前缀。", layer, entity == null ? "" : entity.id, "建议使用 S-/P-/E-/H-/M-/A-/DIM-/TEXT-/TITLE 等前缀。");
                    });
        }

        private boolean invalidLayer(String layer) {
            if (SYSTEM_LAYERS.contains(layer)) {
                return false;
            }
            return ALLOWED_LAYER_PREFIXES.stream().noneMatch(layer::startsWith);
        }
    }

    @Rule(name = "空图层检查", priority = 2)
    public static class EmptyLayerRule extends BaseReviewRule {
        @Condition
        public boolean when(Facts facts) {
            return enabled(facts, "EMPTY_LAYER_CHECK") && safe(context(facts).summary().emptyLayers()).stream().anyMatch(layer -> !SYSTEM_LAYERS.contains(layer));
        }

        @Action
        public void then(Facts facts) {
            safe(context(facts).summary().emptyLayers()).stream()
                    .filter(layer -> !SYSTEM_LAYERS.contains(layer))
                    .forEach(layer -> add(facts, "EMPTY_LAYER_CHECK", "存在空图层：" + layer,
                            "该图层已声明但没有实体。", layer, "", "确认是否为历史残留图层，必要时清理。"));
        }
    }

    @Rule(name = "标题栏检查", priority = 3)
    public static class TitleBlockRule extends BaseReviewRule {
        @Condition
        public boolean when(Facts facts) {
            if (!enabled(facts, "TITLE_BLOCK_REQUIRED")) {
                return false;
            }
            String texts = String.join(" ", safe(context(facts).summary().texts()));
            String blocks = String.join(" ", safe(context(facts).summary().blocks()));
            return !blocks.toUpperCase(Locale.ROOT).contains("TITLE") && !texts.contains("图号") && !texts.contains("版次");
        }

        @Action
        public void then(Facts facts) {
            add(facts, "TITLE_BLOCK_REQUIRED", "缺少可识别标题栏信息",
                    "未检测到标题栏块，且文本中缺少图号/版次关键字。", "", "", "建议补充标准标题栏。");
        }
    }

    @Rule(name = "版次格式检查", priority = 4)
    public static class VersionFormatRule extends BaseReviewRule {
        @Condition
        public boolean when(Facts facts) {
            return enabled(facts, "VERSION_FORMAT") && !validVersion(context(facts).version().versionNo);
        }

        @Action
        public void then(Facts facts) {
            add(facts, "VERSION_FORMAT", "版次格式不规范",
                    "当前版次 " + context(facts).version().versionNo + " 不符合 V1/V2/A/B/R1 等可追溯格式。", "", "", "建议统一版本编码。");
        }
    }

    @Rule(name = "占位文本检查", priority = 5)
    public static class PlaceholderTextRule extends BaseReviewRule {
        @Condition
        public boolean when(Facts facts) {
            return enabled(facts, "TEXT_PLACEHOLDER") && context(facts).entities().stream().anyMatch(this::hasPlaceholder);
        }

        @Action
        public void then(Facts facts) {
            context(facts).entities().stream().filter(this::hasPlaceholder).forEach(entity -> add(facts, "TEXT_PLACEHOLDER", "存在未完成占位文本",
                    "文本实体包含占位内容：" + entity.textValue, entity.layerName, entity.id, "提交审图前请替换为正式设计标注。"));
        }

        private boolean hasPlaceholder(ParsedEntity entity) {
            String text = entity.textValue == null ? "" : entity.textValue;
            String upper = text.toUpperCase(Locale.ROOT);
            return PLACEHOLDERS.stream().anyMatch(flag -> upper.contains(flag) || text.contains(flag));
        }
    }

    @Rule(name = "实体数量异常检查", priority = 6)
    public static class EntityDensityRule extends BaseReviewRule {
        @Condition
        public boolean when(Facts facts) {
            return enabled(facts, "ENTITY_DENSITY") && context(facts).summary().entityCount() < 3;
        }

        @Action
        public void then(Facts facts) {
            add(facts, "ENTITY_DENSITY", "图纸实体数量异常偏少",
                    "仅解析到 " + context(facts).summary().entityCount() + " 个实体，可能为空图或上传错误文件。", "", "", "请确认上传文件是否为正确的模型空间DXF。");
        }
    }

    @Rule(name = "标题栏属性完整性检查", priority = 7)
    public static class TitleAttributeRule extends BaseReviewRule {
        @Condition
        public boolean when(Facts facts) {
            if (!enabled(facts, "TITLE_ATTRIBUTE_REQUIRED") || !hasTitleBlock(facts) || context(facts).summary().entityCount() < 3) {
                return false;
            }
            Map<String, ParsedEntity> attributes = titleAttributesByTag(facts);
            return REQUIRED_TITLE_ATTRIBUTES.stream().anyMatch(attribute -> !attributes.containsKey(attribute));
        }

        @Action
        public void then(Facts facts) {
            Map<String, ParsedEntity> attributes = titleAttributesByTag(facts);
            REQUIRED_TITLE_ATTRIBUTES.stream()
                    .filter(attribute -> !attributes.containsKey(attribute))
                    .forEach(attribute -> add(facts, "TITLE_ATTRIBUTE_REQUIRED", "标题栏属性缺失：" + attribute,
                            "标题栏块中缺少 " + attribute + " 属性，后续版本追踪和报告生成可能无法可靠关联。",
                            "TITLE", "", "建议在标题栏块属性中补充 " + attribute + "。"));
        }
    }

    @Rule(name = "标题栏版次一致性检查", priority = 8)
    public static class VersionTitleConsistencyRule extends BaseReviewRule {
        @Condition
        public boolean when(Facts facts) {
            if (!enabled(facts, "VERSION_TITLE_CONSISTENCY") || !validVersion(context(facts).version().versionNo)) {
                return false;
            }
            ParsedEntity revision = titleAttributesByTag(facts).get("REVISION");
            return revision != null && !normalizeVersion(revision.textValue).equals(normalizeVersion(context(facts).version().versionNo));
        }

        @Action
        public void then(Facts facts) {
            ParsedEntity revision = titleAttributesByTag(facts).get("REVISION");
            add(facts, "VERSION_TITLE_CONSISTENCY", "标题栏版次与上传版次不一致",
                    "标题栏 REVISION 为 " + revision.textValue + "，但系统上传版次为 " + context(facts).version().versionNo + "。",
                    revision.layerName, revision.id, "建议统一标题栏版次与系统版本记录。");
        }
    }

    @Rule(name = "尺寸标注存在性检查", priority = 9)
    public static class DimensionRequiredRule extends BaseReviewRule {
        @Condition
        public boolean when(Facts facts) {
            return enabled(facts, "DIMENSION_REQUIRED")
                    && context(facts).summary().entityCount() >= 3
                    && typeCount(facts, "DIMENSION") == 0;
        }

        @Action
        public void then(Facts facts) {
            add(facts, "DIMENSION_REQUIRED", "缺少尺寸标注",
                    "当前图纸未解析到 DIMENSION 尺寸标注，结构图纸可能缺少尺寸审查依据。",
                    "", "", "建议补充关键结构尺寸，或确认尺寸是否以非标准方式表达。");
        }
    }

    @Rule(name = "尺寸标注图层规范检查", priority = 10)
    public static class DimensionLayerRule extends BaseReviewRule {
        @Condition
        public boolean when(Facts facts) {
            return enabled(facts, "DIMENSION_LAYER_STANDARD") && context(facts).entities().stream().anyMatch(this::invalidDimensionLayer);
        }

        @Action
        public void then(Facts facts) {
            context(facts).entities().stream()
                    .filter(this::invalidDimensionLayer)
                    .forEach(entity -> add(facts, "DIMENSION_LAYER_STANDARD", "尺寸标注图层不规范：" + entity.layerName,
                            "DIMENSION 实体应放在 DIM-* 图层，当前位于 " + entity.layerName + "。",
                            entity.layerName, entity.id, "建议将尺寸标注移动到 DIM-* 图层。"));
        }

        private boolean invalidDimensionLayer(ParsedEntity entity) {
            String layer = entity.layerName == null ? "" : entity.layerName;
            return isType(entity, "DIMENSION") && !layer.startsWith("DIM-");
        }
    }
}
