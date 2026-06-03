package com.shipcad.review.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shipcad.review.domain.AppUser;
import com.shipcad.review.domain.Drawing;
import com.shipcad.review.domain.DrawingVersion;
import com.shipcad.review.domain.IssueStatus;
import com.shipcad.review.domain.ParsedEntity;
import com.shipcad.review.domain.Project;
import com.shipcad.review.domain.RemediationRecord;
import com.shipcad.review.domain.ReportDocument;
import com.shipcad.review.domain.ReviewEvidence;
import com.shipcad.review.domain.ReviewIssue;
import com.shipcad.review.domain.ReviewTask;
import com.shipcad.review.dto.ApiDtos.DrawingRequest;
import com.shipcad.review.dto.ApiDtos.IssueUpdateRequest;
import com.shipcad.review.dto.ApiDtos.ProjectRequest;
import com.shipcad.review.dto.ApiDtos.WorkerEntity;
import com.shipcad.review.dto.ApiDtos.WorkerParseResponse;
import com.shipcad.review.dto.ApiDtos.WorkerSummary;
import com.shipcad.review.repo.DrawingRepository;
import com.shipcad.review.repo.DrawingVersionRepository;
import com.shipcad.review.repo.KnowledgeClauseRepository;
import com.shipcad.review.repo.ParsedEntityRepository;
import com.shipcad.review.repo.ProjectRepository;
import com.shipcad.review.repo.RemediationRecordRepository;
import com.shipcad.review.repo.ReportDocumentRepository;
import com.shipcad.review.repo.ReviewEvidenceRepository;
import com.shipcad.review.repo.ReviewIssueRepository;
import com.shipcad.review.repo.ReviewRuleRepository;
import com.shipcad.review.repo.ReviewTaskRepository;
import jakarta.transaction.Transactional;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ReviewPlatformService {
    private final ProjectRepository projects;
    private final DrawingRepository drawings;
    private final DrawingVersionRepository versions;
    private final ParsedEntityRepository entities;
    private final ReviewRuleRepository rules;
    private final ReviewTaskRepository tasks;
    private final ReviewIssueRepository issues;
    private final ReviewEvidenceRepository evidences;
    private final KnowledgeClauseRepository knowledgeClauses;
    private final RemediationRecordRepository remediations;
    private final ReportDocumentRepository reports;
    private final CadWorkerClient worker;
    private final RuleEngine ruleEngine;
    private final ReviewReportBuilder reportBuilder;
    private final AiGateway aiGateway;
    private final AuditService audit;
    private final ObjectMapper mapper;
    private final ThreadPoolTaskExecutor reviewTaskExecutor;
    private final TransactionTemplate transactionTemplate;
    private final Path storageRoot;

    public ReviewPlatformService(
            ProjectRepository projects,
            DrawingRepository drawings,
            DrawingVersionRepository versions,
            ParsedEntityRepository entities,
            ReviewRuleRepository rules,
            ReviewTaskRepository tasks,
            ReviewIssueRepository issues,
            ReviewEvidenceRepository evidences,
            KnowledgeClauseRepository knowledgeClauses,
            RemediationRecordRepository remediations,
            ReportDocumentRepository reports,
            CadWorkerClient worker,
            RuleEngine ruleEngine,
            ReviewReportBuilder reportBuilder,
            AiGateway aiGateway,
            AuditService audit,
            ObjectMapper mapper,
            ThreadPoolTaskExecutor reviewTaskExecutor,
            PlatformTransactionManager transactionManager,
            @Value("${shipcad.storage-root}") String storageRoot
    ) {
        this.projects = projects;
        this.drawings = drawings;
        this.versions = versions;
        this.entities = entities;
        this.rules = rules;
        this.tasks = tasks;
        this.issues = issues;
        this.evidences = evidences;
        this.knowledgeClauses = knowledgeClauses;
        this.remediations = remediations;
        this.reports = reports;
        this.worker = worker;
        this.ruleEngine = ruleEngine;
        this.reportBuilder = reportBuilder;
        this.aiGateway = aiGateway;
        this.audit = audit;
        this.mapper = mapper;
        this.reviewTaskExecutor = reviewTaskExecutor;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.storageRoot = Path.of(storageRoot).toAbsolutePath().normalize();
    }

    public Project createProject(ProjectRequest request, AppUser actor) {
        Project project = new Project();
        project.id = Ids.next("project");
        project.name = request.name();
        project.shipNo = request.shipNo();
        project.owner = request.owner();
        project.description = request.description();
        project.createdAt = Ids.now();
        projects.save(project);
        audit.record(actor.username, "PROJECT_CREATE", "project", project.id, project);
        return project;
    }

    public Drawing createDrawing(DrawingRequest request, AppUser actor) {
        projects.findById(request.projectId()).orElseThrow(() -> new IllegalArgumentException("项目不存在"));
        Drawing drawing = new Drawing();
        drawing.id = Ids.next("drawing");
        drawing.projectId = request.projectId();
        drawing.drawingNo = request.drawingNo();
        drawing.title = request.title();
        drawing.discipline = request.discipline();
        drawing.createdAt = Ids.now();
        drawings.save(drawing);
        audit.record(actor.username, "DRAWING_CREATE", "drawing", drawing.id, drawing);
        return drawing;
    }

    @Transactional
    public DrawingVersion uploadVersion(String drawingId, String versionNo, MultipartFile file, AppUser actor) throws IOException {
        drawings.findById(drawingId).orElseThrow(() -> new IllegalArgumentException("图纸不存在"));
        String originalName = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();
        if (file.isEmpty() || !(originalName.endsWith(".dxf") || originalName.endsWith(".dwg"))) {
            throw new IllegalArgumentException("当前支持DXF或DWG文件，DWG解析需要安装LibreDWG");
        }
        if (file.getSize() > 20L * 1024 * 1024) {
            throw new IllegalArgumentException("文件超过20MB限制");
        }
        String id = Ids.next("version");
        Path folder = storageRoot.resolve("uploads").resolve(drawingId);
        Files.createDirectories(folder);
        Path target = folder.resolve(id + "_" + file.getOriginalFilename()).normalize();
        file.transferTo(target);

        DrawingVersion version = new DrawingVersion();
        version.id = id;
        version.drawingId = drawingId;
        version.versionNo = versionNo;
        version.fileName = file.getOriginalFilename();
        version.filePath = target.toString();
        version.fileSha256 = sha256(target);
        version.uploadedBy = actor.username;
        version.uploadedAt = Ids.now();
        version.parseStatus = "PENDING";
        version.parseSummaryJson = "{}";
        versions.save(version);
        audit.record(actor.username, "VERSION_UPLOAD", "version", id, Map.of("fileName", version.fileName, "sha256", version.fileSha256));
        return version;
    }

    @Transactional
    public DrawingVersion parseVersion(String versionId, AppUser actor) {
        DrawingVersion version = versions.findById(versionId).orElseThrow(() -> new IllegalArgumentException("版本不存在"));
        WorkerParseResponse parsed = worker.parse(Path.of(version.filePath));
        entities.deleteByVersionId(versionId);
        for (WorkerEntity workerEntity : parsed.entities()) {
            ParsedEntity entity = new ParsedEntity();
            entity.id = Ids.next("entity");
            entity.versionId = versionId;
            entity.entityType = workerEntity.entityType();
            entity.layerName = workerEntity.layer();
            entity.textValue = workerEntity.text();
            entity.blockName = workerEntity.blockName();
            entity.x = workerEntity.x();
            entity.y = workerEntity.y();
            entity.rawJson = toJson(workerEntity);
            entities.save(entity);
        }
        version.parseStatus = "SUCCESS";
        version.parseSummaryJson = toJson(parsed.summary());
        versions.save(version);
        audit.record(actor.username, "VERSION_PARSE", "version", versionId, Map.of("entityCount", parsed.summary().entityCount()));
        return version;
    }

    public ReviewTask createReviewTask(String versionId, AppUser actor) {
        versions.findById(versionId).orElseThrow(() -> new IllegalArgumentException("版本不存在"));
        ReviewTask task = new ReviewTask();
        task.id = Ids.next("task");
        task.versionId = versionId;
        task.status = "PENDING";
        task.startedAt = Ids.now();
        task.errorMessage = "";
        tasks.save(task);
        audit.record(actor.username, "REVIEW_QUEUED", "task", task.id, Map.of("versionId", versionId));

        String actorUsername = actor.username;
        reviewTaskExecutor.execute(() ->
                transactionTemplate.executeWithoutResult(status -> runQueuedReviewTask(task.id, actorUsername))
        );
        return task;
    }

    public ReviewTask retryReviewTask(String taskId, AppUser actor) {
        ReviewTask oldTask = tasks.findById(taskId).orElseThrow(() -> new IllegalArgumentException("审查任务不存在"));
        return createReviewTask(oldTask.versionId, actor);
    }

    private void runQueuedReviewTask(String taskId, String actorUsername) {
        ReviewTask task = tasks.findById(taskId).orElseThrow(() -> new IllegalArgumentException("审查任务不存在"));
        task.status = "RUNNING";
        task.startedAt = Ids.now();
        task.errorMessage = "";
        tasks.save(task);

        try {
            DrawingVersion version = versions.findById(task.versionId).orElseThrow(() -> new IllegalArgumentException("版本不存在"));
            if (!"SUCCESS".equals(version.parseStatus)) {
                version = parseVersion(task.versionId, systemActor(actorUsername));
            }
            WorkerSummary summary = fromJson(version.parseSummaryJson, WorkerSummary.class);
            List<ParsedEntity> parsedEntities = entities.findByVersionId(task.versionId);
            List<ReviewIssue> generated = ruleEngine.run(task.id, version, summary, parsedEntities, rules.findByEnabledTrue(), knowledgeClauses.findAll());
            issues.saveAll(generated);
            evidences.saveAll(generated.stream()
                    .flatMap(issue -> safeEvidences(issue).stream())
                    .toList());

            task.status = "FINISHED";
            task.finishedAt = Ids.now();
            task.issueCount = generated.size();
            tasks.save(task);
            audit.record(actorUsername, "REVIEW_FINISHED", "task", task.id, Map.of("versionId", task.versionId, "issueCount", generated.size()));
        } catch (Exception exception) {
            String message = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
            versions.findById(task.versionId).ifPresent(version -> {
                if (!"SUCCESS".equals(version.parseStatus)) {
                    version.parseStatus = "FAILED";
                    versions.save(version);
                }
            });
            task.status = "FAILED";
            task.finishedAt = Ids.now();
            task.errorMessage = message;
            tasks.save(task);
            audit.record(actorUsername, "REVIEW_FAILED", "task", task.id, Map.of("versionId", task.versionId, "error", message));
        }
    }

    public List<ReviewIssue> listIssues(String taskId, String versionId) {
        List<ReviewIssue> result;
        if (taskId != null && !taskId.isBlank()) {
            result = issues.findByTaskId(taskId);
        } else if (versionId != null && !versionId.isBlank()) {
            result = issues.findByVersionId(versionId);
        } else {
            result = issues.findAll();
        }
        return attachEvidence(result);
    }

    public List<ReviewEvidence> listIssueEvidence(String issueId) {
        issues.findById(issueId).orElseThrow(() -> new IllegalArgumentException("问题不存在"));
        return evidences.findByIssueId(issueId);
    }

    public com.shipcad.review.domain.AiExplanation explainIssue(String issueId) {
        ReviewIssue issue = issues.findById(issueId).orElseThrow(() -> new IllegalArgumentException("问题不存在"));
        return aiGateway.explain(attachEvidence(issue));
    }

    public ReviewIssue updateIssue(String issueId, IssueUpdateRequest request, AppUser actor) {
        ReviewIssue issue = issues.findById(issueId).orElseThrow(() -> new IllegalArgumentException("问题不存在"));
        if (request.status() != null) {
            issue.status = request.status();
        }
        if (request.assignee() != null) {
            issue.assignee = request.assignee();
        }
        issue.updatedAt = Ids.now();
        issues.save(issue);

        RemediationRecord record = new RemediationRecord();
        record.id = Ids.next("remediation");
        record.issueId = issueId;
        record.operator = actor.username;
        record.action = "UPDATE";
        record.note = request.note();
        record.createdAt = Ids.now();
        remediations.save(record);
        audit.record(actor.username, "ISSUE_UPDATE", "issue", issueId, request);
        return attachEvidence(issue);
    }

    public ReportDocument createReport(String taskId, AppUser actor) {
        ReviewTask task = tasks.findById(taskId).orElseThrow(() -> new IllegalArgumentException("审查任务不存在"));
        if (!"FINISHED".equals(task.status)) {
            throw new IllegalArgumentException("审查任务尚未完成，暂不能生成报告");
        }
        DrawingVersion version = versions.findById(task.versionId).orElseThrow();
        Drawing drawing = drawings.findById(version.drawingId).orElseThrow();
        Project project = projects.findById(drawing.projectId).orElseThrow();
        List<ReviewIssue> taskIssues = attachEvidence(issues.findByTaskId(taskId));
        WorkerSummary summary = fromJson(version.parseSummaryJson, WorkerSummary.class);
        List<ParsedEntity> parsedEntities = entities.findByVersionId(version.id);
        ReportDocument report = new ReportDocument();
        report.id = Ids.next("report");
        report.taskId = taskId;
        report.versionId = version.id;
        report.content = reportBuilder.build(project, drawing, version, summary, taskIssues, parsedEntities);
        report.createdAt = Ids.now();
        reports.save(report);
        audit.record(actor.username, "REPORT_CREATE", "report", report.id, Map.of("taskId", taskId));
        return report;
    }

    public Map<String, Object> compareVersions(String leftId, String rightId) {
        WorkerSummary left = fromJson(versions.findById(leftId).orElseThrow().parseSummaryJson, WorkerSummary.class);
        WorkerSummary right = fromJson(versions.findById(rightId).orElseThrow().parseSummaryJson, WorkerSummary.class);
        Map<String, Object> result = new HashMap<>();
        result.put("entityCountDelta", right.entityCount() - left.entityCount());
        result.put("addedLayers", right.layers().stream().filter(layer -> !left.layers().contains(layer)).toList());
        result.put("removedLayers", left.layers().stream().filter(layer -> !right.layers().contains(layer)).toList());
        Map<String, Integer> deltas = new HashMap<>();
        for (String type : right.typeCounts().keySet()) {
            deltas.put(type, right.typeCounts().getOrDefault(type, 0) - left.typeCounts().getOrDefault(type, 0));
        }
        for (String type : left.typeCounts().keySet()) {
            deltas.putIfAbsent(type, right.typeCounts().getOrDefault(type, 0) - left.typeCounts().getOrDefault(type, 0));
        }
        result.put("typeDeltas", deltas);
        return result;
    }

    public WorkerSummary summaryOf(DrawingVersion version) {
        return fromJson(version.parseSummaryJson, WorkerSummary.class);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> geometryOf(ParsedEntity entity) {
        if (entity.rawJson == null || entity.rawJson.isBlank()) {
            return Map.of();
        }
        try {
            Map<String, Object> raw = mapper.readValue(entity.rawJson, Map.class);
            Object geometry = raw.get("geometry");
            return geometry instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
        } catch (JsonProcessingException e) {
            return Map.of();
        }
    }

    private String sha256(Path file) {
        try (InputStream input = Files.newInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                digest.update(buffer, 0, read);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException | NoSuchAlgorithmException e) {
            throw new IllegalStateException("计算文件哈希失败", e);
        }
    }

    private String toJson(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("JSON序列化失败", e);
        }
    }

    private <T> T fromJson(String value, Class<T> type) {
        try {
            return mapper.readValue(value == null || value.isBlank() ? "{}" : value, type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("JSON解析失败", e);
        }
    }

    private AppUser systemActor(String username) {
        AppUser user = new AppUser();
        user.username = username;
        return user;
    }

    private ReviewIssue attachEvidence(ReviewIssue issue) {
        issue.evidences = evidences.findByIssueId(issue.id);
        issue.aiExplanation = aiGateway.explain(issue);
        return issue;
    }

    private List<ReviewIssue> attachEvidence(List<ReviewIssue> source) {
        if (source.isEmpty()) {
            return source;
        }
        List<String> issueIds = source.stream().map(issue -> issue.id).toList();
        Map<String, List<ReviewEvidence>> evidenceByIssue = evidences.findByIssueIdIn(issueIds).stream()
                .collect(Collectors.groupingBy(evidence -> evidence.issueId));
        source.forEach(issue -> {
            issue.evidences = evidenceByIssue.getOrDefault(issue.id, List.of());
            issue.aiExplanation = aiGateway.explain(issue);
        });
        return source;
    }

    private List<ReviewEvidence> safeEvidences(ReviewIssue issue) {
        return issue.evidences == null ? List.of() : issue.evidences;
    }
}
