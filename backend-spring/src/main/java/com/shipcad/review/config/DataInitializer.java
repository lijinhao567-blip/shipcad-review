package com.shipcad.review.config;

import com.shipcad.review.domain.AppUser;
import com.shipcad.review.domain.KnowledgeClause;
import com.shipcad.review.domain.ReviewRule;
import com.shipcad.review.domain.Severity;
import com.shipcad.review.domain.UserRole;
import com.shipcad.review.repo.AppUserRepository;
import com.shipcad.review.repo.KnowledgeClauseRepository;
import com.shipcad.review.repo.ReviewRuleRepository;
import com.shipcad.review.service.AuthService;
import com.shipcad.review.service.Ids;
import jakarta.annotation.PostConstruct;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class DataInitializer {
    private final AppUserRepository users;
    private final ReviewRuleRepository rules;
    private final KnowledgeClauseRepository clauses;
    private final AuthService auth;

    public DataInitializer(AppUserRepository users, ReviewRuleRepository rules, KnowledgeClauseRepository clauses, AuthService auth) {
        this.users = users;
        this.rules = rules;
        this.clauses = clauses;
        this.auth = auth;
    }

    @PostConstruct
    public void init() {
        if (users.findByUsername("admin").isEmpty()) {
            AppUser user = new AppUser();
            user.id = "user_admin";
            user.username = "admin";
            user.displayName = "系统管理员";
            user.role = UserRole.ADMIN;
            user.passwordHash = auth.encode("admin123");
            users.save(user);
        }

        addClauseIfMissing("BASIS_LAYER_NAMING", "图层命名约定依据",
                "项目内部审查依据：图层名称应采用约定的专业或用途前缀，便于图纸结构化解析、问题定位和后续版本对比。",
                "MVP_INTERNAL_RULE_BASIS", "cad,layer,naming",
                "按项目约定使用 S-/P-/E-/H-/M-/A-/DIM-/TEXT-/TITLE 等前缀，并清理临时命名。");
        addClauseIfMissing("BASIS_EMPTY_LAYER", "空图层清理依据",
                "项目内部审查依据：已声明但没有实体的非系统图层会增加图纸维护成本，并可能干扰解析摘要和版本差异判断。",
                "MVP_INTERNAL_RULE_BASIS", "cad,layer,cleanup",
                "确认空图层是否为历史保留；若无用途，应在提交审查前清理。");
        addClauseIfMissing("BASIS_TITLE_TRACEABILITY", "标题栏可追溯依据",
                "项目内部审查依据：标题栏应提供图号、版次等关键元数据，使上传版本、审查报告和后续整改记录能够可靠关联。",
                "MVP_INTERNAL_RULE_BASIS", "title-block,traceability",
                "补充标准标题栏块，或完善标题栏属性中的图号和版次。");
        addClauseIfMissing("BASIS_VERSION_TRACEABILITY", "版次可追溯依据",
                "项目内部审查依据：图纸版本号应采用稳定格式，并与标题栏记录保持一致，避免整改和复核阶段出现版本歧义。",
                "MVP_INTERNAL_RULE_BASIS", "version,traceability",
                "统一系统上传版次与标题栏版次，并使用 V1/V2/A/B/R1 等可追踪格式。");
        addClauseIfMissing("BASIS_TEXT_COMPLETENESS", "文本完整性依据",
                "项目内部审查依据：TBD、TODO、XXX、待定、未定等占位内容表示设计表达尚未完成，不应进入正式审查或交付报告。",
                "MVP_INTERNAL_RULE_BASIS", "text,completeness",
                "将占位文本替换为正式设计说明或明确删除。");
        addClauseIfMissing("BASIS_OCR_TEXT_EVIDENCE", "OCR文字证据审查依据",
                "项目内部审查依据：OCR识别结果可作为CAD结构化文本之外的补充证据，用于发现截图或非标准文本实体中的占位内容。",
                "MVP_INTERNAL_RULE_BASIS", "ocr,text,evidence",
                "复核OCR定位区域，将占位文本替换为正式设计说明。");
        addClauseIfMissing("BASIS_ENTITY_DENSITY", "实体数量合理性依据",
                "项目内部审查依据：结构图纸通常应包含足够的几何实体、标注和标题信息；实体数量异常偏少时，应优先排除空图、错传或解析失败。",
                "MVP_INTERNAL_RULE_BASIS", "cad,parse,quality",
                "确认上传文件是否正确，必要时重新导出 DXF 或检查 CAD Worker 解析日志。");
        addClauseIfMissing("BASIS_DIMENSION_EVIDENCE", "尺寸标注审查依据",
                "项目内部审查依据：结构图纸应保留关键尺寸标注，且尺寸实体应放置在约定的尺寸图层，以便审图人员快速定位和复核。",
                "MVP_INTERNAL_RULE_BASIS", "dimension,layer,review",
                "补充关键结构尺寸，并将 DIMENSION 实体移动到 DIM-* 图层。");
        addClauseIfMissing("BASIS_VISUAL_CAD_CROSS_CHECK", "视觉证据与CAD解析交叉校验依据",
                "项目内部审查依据：视觉识别与CAD结构化解析结果不一致时，应暴露为复核提示，避免单一证据源遗漏标题栏、符号或关键标注。",
                "MVP_INTERNAL_RULE_BASIS", "vision,cad,cross-check",
                "复核视觉检测区域、CAD块命名和DXF导出方式，必要时完善解析适配。");

        addRuleIfMissing("LAYER_NAME_STANDARD", "图层命名规范检查", "检查图层是否使用推荐前缀。", Severity.MEDIUM, "BASIS_LAYER_NAMING");
        addRuleIfMissing("EMPTY_LAYER_CHECK", "空图层检查", "发现没有任何实体的非系统图层。", Severity.LOW, "BASIS_EMPTY_LAYER");
        addRuleIfMissing("TITLE_BLOCK_REQUIRED", "标题栏检查", "检查是否存在标题栏块或图号/版次文字。", Severity.HIGH, "BASIS_TITLE_TRACEABILITY");
        addRuleIfMissing("VERSION_FORMAT", "版次格式检查", "检查版次是否符合可追踪格式。", Severity.MEDIUM, "BASIS_VERSION_TRACEABILITY");
        addRuleIfMissing("TEXT_PLACEHOLDER", "占位文本检查", "检查 TBD、待定、XXX 等未完成标注。", Severity.MEDIUM, "BASIS_TEXT_COMPLETENESS");
        addRuleIfMissing("ENTITY_DENSITY", "实体数量异常检查", "检查图纸实体数量是否异常偏少。", Severity.HIGH, "BASIS_ENTITY_DENSITY");
        addRuleIfMissing("TITLE_ATTRIBUTE_REQUIRED", "标题栏属性完整性检查", "检查标题栏块属性是否包含图号和版次。", Severity.HIGH, "BASIS_TITLE_TRACEABILITY");
        addRuleIfMissing("VERSION_TITLE_CONSISTENCY", "标题栏版次一致性检查", "检查标题栏版次是否与系统上传版次一致。", Severity.MEDIUM, "BASIS_VERSION_TRACEABILITY");
        addRuleIfMissing("DIMENSION_REQUIRED", "尺寸标注存在性检查", "检查结构图纸是否包含尺寸标注。", Severity.MEDIUM, "BASIS_DIMENSION_EVIDENCE");
        addRuleIfMissing("DIMENSION_LAYER_STANDARD", "尺寸标注图层规范检查", "检查尺寸标注是否位于 DIM-* 图层。", Severity.MEDIUM, "BASIS_DIMENSION_EVIDENCE");
        addRuleIfMissing("OCR_PLACEHOLDER_TEXT", "OCR占位文本检查", "检查OCR文字证据中是否存在 TBD、待定、XXX 等未完成标注。", Severity.MEDIUM, "BASIS_OCR_TEXT_EVIDENCE");
        addRuleIfMissing("YOLO_TITLE_BLOCK_CAD_MISSING", "视觉标题栏与CAD解析交叉校验", "检查YOLO识别到标题栏但CAD解析未提取标题栏块的证据冲突。", Severity.MEDIUM, "BASIS_VISUAL_CAD_CROSS_CHECK");
    }

    private void addClauseIfMissing(String code, String title, String content, String source, String tags, String remediationHint) {
        if (clauses.findByCode(code).isPresent()) {
            return;
        }
        KnowledgeClause clause = new KnowledgeClause();
        clause.id = Ids.next("clause");
        clause.code = code;
        clause.title = title;
        clause.content = content;
        clause.source = source;
        clause.tags = tags;
        clause.remediationHint = remediationHint;
        clause.createdAt = Ids.now();
        clauses.save(clause);
    }

    private void addRuleIfMissing(String code, String name, String description, Severity severity, String knowledgeClauseCode) {
        String id = "rule_" + code.toLowerCase(Locale.ROOT);
        ReviewRule rule = rules.findById(id).orElse(null);
        if (rule == null) {
            rule = new ReviewRule();
            rule.id = id;
            rule.code = code;
            rule.name = name;
            rule.description = description;
            rule.severity = severity;
            rule.enabled = true;
            rule.knowledgeClauseCode = knowledgeClauseCode;
            rules.save(rule);
            return;
        }
        if (rule.knowledgeClauseCode == null || rule.knowledgeClauseCode.isBlank()) {
            rule.knowledgeClauseCode = knowledgeClauseCode;
            rules.save(rule);
        }
    }
}
