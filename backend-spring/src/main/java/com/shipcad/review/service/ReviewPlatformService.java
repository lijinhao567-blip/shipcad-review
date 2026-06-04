package com.shipcad.review.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shipcad.review.domain.AppUser;
import com.shipcad.review.domain.Drawing;
import com.shipcad.review.domain.DrawingVersion;
import com.shipcad.review.domain.EvidenceType;
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
import com.shipcad.review.dto.ApiDtos.OcrRegion;
import com.shipcad.review.dto.ApiDtos.OcrResponse;
import com.shipcad.review.dto.ApiDtos.ProjectRequest;
import com.shipcad.review.dto.ApiDtos.ReviewTaskRequest;
import com.shipcad.review.dto.ApiDtos.VisionDetection;
import com.shipcad.review.dto.ApiDtos.VisionDetectionResponse;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
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
    private static final int DEFAULT_RENDER_WIDTH = 1600;
    private static final int DEFAULT_RENDER_HEIGHT = 1200;

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
    private final VisionWorkerClient visionWorker;
    private final OcrWorkerClient ocrWorker;
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
            VisionWorkerClient visionWorker,
            OcrWorkerClient ocrWorker,
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
        this.visionWorker = visionWorker;
        this.ocrWorker = ocrWorker;
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

    @Transactional
    public Path renderVersionImage(String versionId, boolean force, AppUser actor) throws IOException {
        DrawingVersion version = versions.findById(versionId).orElseThrow(() -> new IllegalArgumentException("版本不存在"));
        Path target = renderedImagePath(versionId);
        if (!force && Files.isRegularFile(target) && Files.size(target) > 0) {
            return target;
        }
        byte[] png = worker.render(Path.of(version.filePath), DEFAULT_RENDER_WIDTH, DEFAULT_RENDER_HEIGHT);
        if (png == null || png.length == 0) {
            throw new IllegalStateException("CAD Worker returned an empty rendered image");
        }
        Files.createDirectories(target.getParent());
        Files.write(target, png);
        audit.record(actor.username, "VERSION_RENDER", "version", versionId, Map.of(
                "imagePath", target.toString(),
                "width", DEFAULT_RENDER_WIDTH,
                "height", DEFAULT_RENDER_HEIGHT
        ));
        return target;
    }

    @Transactional
    public List<ReviewEvidence> runVisionDetection(String versionId, MultipartFile file, double confidence, AppUser actor) throws IOException {
        versions.findById(versionId).orElseThrow(() -> new IllegalArgumentException("版本不存在"));
        validateVisionConfidence(confidence);
        Path image = storeVisionImage(versionId, file);
        return runVisionDetectionOnImage(versionId, null, image, confidence, actor, "VISION_DETECT", "uploaded-image");
    }

    @Transactional
    public List<ReviewEvidence> runVisionDetectionFromRenderedImage(String versionId, double confidence, boolean forceRender, AppUser actor) throws IOException {
        versions.findById(versionId).orElseThrow(() -> new IllegalArgumentException("版本不存在"));
        validateVisionConfidence(confidence);
        Path image = renderVersionImage(versionId, forceRender, actor);
        return runVisionDetectionOnImage(versionId, null, image, confidence, actor, "VISION_DETECT_RENDERED", "rendered-version-image");
    }

    private List<ReviewEvidence> runVisionDetectionOnImage(String versionId, String taskId, Path image, double confidence, AppUser actor, String auditAction, String inputSource) {
        VisionDetectionResponse response = visionWorker.detect(image, confidence);
        List<VisionDetection> detections = response == null || response.detections() == null ? List.of() : response.detections();
        List<ReviewEvidence> generated = new ArrayList<>();
        for (int index = 0; index < detections.size(); index += 1) {
            generated.add(visionEvidence(versionId, taskId, image, response, detections.get(index), index, inputSource));
        }
        evidences.saveAll(generated);

        Map<String, Object> detail = new HashMap<>();
        detail.put("versionId", versionId);
        detail.put("taskId", taskId == null ? "" : taskId);
        detail.put("imagePath", image.toString());
        detail.put("inputSource", inputSource);
        detail.put("confidenceThreshold", confidence);
        detail.put("detectionCount", generated.size());
        detail.put("engine", response == null ? "" : response.engine());
        audit.record(actor.username, auditAction, "version", versionId, detail);
        return generated;
    }

    @Transactional
    public List<ReviewEvidence> runOcrRecognition(String versionId, MultipartFile file, double confidence, AppUser actor) throws IOException {
        versions.findById(versionId).orElseThrow(() -> new IllegalArgumentException("版本不存在"));
        validateOcrConfidence(confidence);
        Path image = storeOcrImage(versionId, file);
        return runOcrRecognitionOnImage(versionId, null, image, confidence, actor, "OCR_RECOGNIZE", "uploaded-image");
    }

    @Transactional
    public List<ReviewEvidence> runOcrRecognitionFromRenderedImage(String versionId, double confidence, boolean forceRender, AppUser actor) throws IOException {
        versions.findById(versionId).orElseThrow(() -> new IllegalArgumentException("版本不存在"));
        validateOcrConfidence(confidence);
        Path image = renderVersionImage(versionId, forceRender, actor);
        return runOcrRecognitionOnImage(versionId, null, image, confidence, actor, "OCR_RECOGNIZE_RENDERED", "rendered-version-image");
    }

    private List<ReviewEvidence> runOcrRecognitionOnImage(String versionId, String taskId, Path image, double confidence, AppUser actor, String auditAction, String inputSource) {
        OcrResponse response = ocrWorker.recognize(image, confidence);
        List<OcrRegion> regions = response == null || response.regions() == null ? List.of() : response.regions();
        List<ReviewEvidence> generated = new ArrayList<>();
        for (int index = 0; index < regions.size(); index += 1) {
            generated.add(ocrEvidence(versionId, taskId, image, response, regions.get(index), index, inputSource));
        }
        evidences.saveAll(generated);

        Map<String, Object> detail = new HashMap<>();
        detail.put("versionId", versionId);
        detail.put("taskId", taskId == null ? "" : taskId);
        detail.put("imagePath", image.toString());
        detail.put("inputSource", inputSource);
        detail.put("confidenceThreshold", confidence);
        detail.put("regionCount", generated.size());
        detail.put("engine", response == null ? "" : response.engine());
        detail.put("language", response == null ? "" : response.language());
        audit.record(actor.username, auditAction, "version", versionId, detail);
        return generated;
    }

    public ReviewTask createReviewTask(ReviewTaskRequest request, AppUser actor) {
        return createReviewTask(
                request.versionId(),
                bool(request.autoVision()),
                bool(request.autoOcr()),
                bool(request.forceRender()),
                confidenceOrDefault(request.visionConfidence(), 0.25),
                confidenceOrDefault(request.ocrConfidence(), 0.5),
                actor
        );
    }

    public ReviewTask createReviewTask(String versionId, AppUser actor) {
        return createReviewTask(versionId, false, false, false, 0.25, 0.5, actor);
    }

    private ReviewTask createReviewTask(String versionId, boolean autoVision, boolean autoOcr, boolean forceRender,
                                        double visionConfidence, double ocrConfidence, AppUser actor) {
        versions.findById(versionId).orElseThrow(() -> new IllegalArgumentException("版本不存在"));
        if (autoVision) {
            validateVisionConfidence(visionConfidence);
        }
        if (autoOcr) {
            validateOcrConfidence(ocrConfidence);
        }
        ReviewTask task = new ReviewTask();
        task.id = Ids.next("task");
        task.versionId = versionId;
        task.status = "PENDING";
        task.startedAt = Ids.now();
        task.errorMessage = "";
        task.autoVision = autoVision;
        task.autoOcr = autoOcr;
        task.forceRender = forceRender;
        task.visionConfidence = visionConfidence;
        task.ocrConfidence = ocrConfidence;
        tasks.save(task);
        audit.record(actor.username, "REVIEW_QUEUED", "task", task.id, Map.of(
                "versionId", versionId,
                "autoVision", autoVision,
                "autoOcr", autoOcr,
                "forceRender", forceRender,
                "visionConfidence", visionConfidence,
                "ocrConfidence", ocrConfidence
        ));

        String actorUsername = actor.username;
        reviewTaskExecutor.execute(() ->
                transactionTemplate.executeWithoutResult(status -> runQueuedReviewTask(task.id, actorUsername))
        );
        return task;
    }

    public ReviewTask retryReviewTask(String taskId, AppUser actor) {
        ReviewTask oldTask = tasks.findById(taskId).orElseThrow(() -> new IllegalArgumentException("审查任务不存在"));
        return createReviewTask(
                oldTask.versionId,
                bool(oldTask.autoVision),
                bool(oldTask.autoOcr),
                bool(oldTask.forceRender),
                confidenceOrDefault(oldTask.visionConfidence, 0.25),
                confidenceOrDefault(oldTask.ocrConfidence, 0.5),
                actor
        );
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
            collectAutomaticEvidence(task, systemActor(actorUsername));
            WorkerSummary summary = fromJson(version.parseSummaryJson, WorkerSummary.class);
            List<ParsedEntity> parsedEntities = entities.findByVersionId(task.versionId);
            List<ReviewEvidence> versionEvidences = evidences.findByVersionId(task.versionId).stream()
                    .filter(evidence -> evidence.issueId == null || evidence.issueId.isBlank())
                    .filter(evidence -> evidence.taskId == null || evidence.taskId.isBlank() || task.id.equals(evidence.taskId))
                    .toList();
            List<ReviewIssue> generated = ruleEngine.run(
                    task.id,
                    version,
                    summary,
                    parsedEntities,
                    rules.findByEnabledTrue(),
                    knowledgeClauses.findAll(),
                    versionEvidences
            );
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

    private void collectAutomaticEvidence(ReviewTask task, AppUser actor) throws IOException {
        if (!bool(task.autoVision) && !bool(task.autoOcr)) {
            return;
        }
        Path image = renderVersionImage(task.versionId, bool(task.forceRender), actor);
        if (bool(task.autoVision)) {
            runVisionDetectionOnImage(
                    task.versionId,
                    task.id,
                    image,
                    confidenceOrDefault(task.visionConfidence, 0.25),
                    actor,
                    "REVIEW_AUTO_VISION_DETECT",
                    "review-rendered-version-image"
            );
        }
        if (bool(task.autoOcr)) {
            runOcrRecognitionOnImage(
                    task.versionId,
                    task.id,
                    image,
                    confidenceOrDefault(task.ocrConfidence, 0.5),
                    actor,
                    "REVIEW_AUTO_OCR_RECOGNIZE",
                    "review-rendered-version-image"
            );
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

    public List<ReviewEvidence> listVersionEvidence(String versionId, EvidenceType type) {
        versions.findById(versionId).orElseThrow(() -> new IllegalArgumentException("版本不存在"));
        List<ReviewEvidence> source = evidences.findByVersionId(versionId);
        if (type == null) {
            return source;
        }
        return source.stream()
                .filter(evidence -> type.equals(evidence.evidenceType))
                .toList();
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

    private ReviewEvidence visionEvidence(String versionId, String taskId, Path image, VisionDetectionResponse response, VisionDetection detection, int index, String inputSource) {
        String className = detection.className() == null || detection.className().isBlank() ? "unknown" : detection.className();
        String confidenceText = detection.confidence() == null ? "-" : String.format(Locale.ROOT, "%.2f", detection.confidence());
        Map<String, Object> payload = new HashMap<>();
        payload.put("taskId", taskId);
        payload.put("inputSource", inputSource);
        payload.put("classId", detection.classId());
        payload.put("className", className);
        payload.put("confidence", detection.confidence());
        payload.put("xyxy", detection.xyxy());
        payload.put("imagePath", image.toString());
        payload.put("imageWidth", response == null ? null : response.imageWidth());
        payload.put("imageHeight", response == null ? null : response.imageHeight());
        payload.put("engine", response == null ? "" : response.engine());

        ReviewEvidence evidence = new ReviewEvidence();
        evidence.id = Ids.next("evidence");
        evidence.issueId = null;
        evidence.taskId = taskId;
        evidence.versionId = versionId;
        evidence.ruleCode = "VISION_DETECTION";
        evidence.evidenceType = EvidenceType.YOLO_SYMBOL;
        evidence.sourceId = "symbol:" + className + "#" + index;
        evidence.sourceLabel = "vision_worker.yolov8";
        evidence.summary = "检测到视觉符号 " + className + "，置信度 " + confidenceText + "，边界框 " + bboxText(detection.xyxy());
        evidence.payloadJson = toJson(payload);
        evidence.confidence = detection.confidence();
        evidence.createdAt = Ids.now();
        return evidence;
    }

    private ReviewEvidence ocrEvidence(String versionId, String taskId, Path image, OcrResponse response, OcrRegion region, int index, String inputSource) {
        String text = region.text() == null ? "" : region.text();
        String confidenceText = region.confidence() == null ? "-" : String.format(Locale.ROOT, "%.2f", region.confidence());
        Map<String, Object> payload = new HashMap<>();
        payload.put("taskId", taskId);
        payload.put("inputSource", inputSource);
        payload.put("text", text);
        payload.put("confidence", region.confidence());
        payload.put("xyxy", region.xyxy());
        payload.put("language", region.language());
        payload.put("imagePath", image.toString());
        payload.put("imageWidth", response == null ? null : response.imageWidth());
        payload.put("imageHeight", response == null ? null : response.imageHeight());
        payload.put("engine", response == null ? "" : response.engine());

        ReviewEvidence evidence = new ReviewEvidence();
        evidence.id = Ids.next("evidence");
        evidence.issueId = null;
        evidence.taskId = taskId;
        evidence.versionId = versionId;
        evidence.ruleCode = "OCR_RECOGNITION";
        evidence.evidenceType = EvidenceType.OCR_TEXT;
        evidence.sourceId = "ocr:text#" + index;
        evidence.sourceLabel = "ocr_worker." + (response == null || response.engine() == null || response.engine().isBlank() ? "unknown" : response.engine());
        evidence.summary = "OCR识别文字 " + text + "，置信度 " + confidenceText + "，边界框 " + bboxText(region.xyxy());
        evidence.payloadJson = toJson(payload);
        evidence.confidence = region.confidence();
        evidence.createdAt = Ids.now();
        return evidence;
    }

    private Path storeVisionImage(String versionId, MultipartFile file) throws IOException {
        return storeEvidenceImage(versionId, file, "vision", "vision", "vision-image.png", "视觉检测");
    }

    private Path storeOcrImage(String versionId, MultipartFile file) throws IOException {
        return storeEvidenceImage(versionId, file, "ocr", "ocr", "ocr-image.png", "OCR识别");
    }

    private Path renderedImagePath(String versionId) {
        return storageRoot.resolve("rendered").resolve(versionId).resolve("render.png").toAbsolutePath().normalize();
    }

    private void validateVisionConfidence(double confidence) {
        if (confidence <= 0 || confidence > 1) {
            throw new IllegalArgumentException("视觉检测置信度必须在0到1之间");
        }
    }

    private void validateOcrConfidence(double confidence) {
        if (confidence < 0 || confidence > 1) {
            throw new IllegalArgumentException("OCR置信度必须在0到1之间");
        }
    }

    private boolean bool(Boolean value) {
        return Boolean.TRUE.equals(value);
    }

    private double confidenceOrDefault(Double value, double fallback) {
        return value == null ? fallback : value;
    }

    private Path storeEvidenceImage(String versionId, MultipartFile file, String folderName, String idPrefix, String fallbackName, String label) throws IOException {
        String fileName = safeFileName(file.getOriginalFilename(), fallbackName);
        String lowerName = fileName.toLowerCase(Locale.ROOT);
        if (file.isEmpty() || !(lowerName.endsWith(".png") || lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg"))) {
            throw new IllegalArgumentException(label + "当前仅支持PNG或JPG图片");
        }
        if (file.getSize() > 20L * 1024 * 1024) {
            throw new IllegalArgumentException(label + "图片超过20MB限制");
        }
        Path folder = storageRoot.resolve(folderName).resolve(versionId).toAbsolutePath().normalize();
        Files.createDirectories(folder);
        Path target = folder.resolve(Ids.next(idPrefix) + "_" + fileName).normalize();
        if (!target.startsWith(folder)) {
            throw new IllegalArgumentException(label + "文件路径非法");
        }
        file.transferTo(target);
        return target;
    }

    private String safeFileName(String originalName, String fallback) {
        String value = originalName == null || originalName.isBlank() ? fallback : originalName.trim();
        value = value.replace('\\', '/');
        int lastSeparator = value.lastIndexOf('/');
        if (lastSeparator >= 0) {
            value = value.substring(lastSeparator + 1);
        }
        value = value.replaceAll("[^A-Za-z0-9._-]", "_");
        if (value.isBlank() || ".".equals(value) || "..".equals(value)) {
            return fallback;
        }
        return value;
    }

    private String bboxText(List<Double> xyxy) {
        if (xyxy == null || xyxy.isEmpty()) {
            return "[]";
        }
        return xyxy.stream()
                .map(value -> value == null ? "-" : String.format(Locale.ROOT, "%.1f", value))
                .collect(Collectors.joining(", ", "[", "]"));
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
