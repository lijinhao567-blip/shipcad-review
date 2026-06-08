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
import com.shipcad.review.domain.ReviewRule;
import com.shipcad.review.domain.ReviewTask;
import com.shipcad.review.domain.ReviewTaskStep;
import com.shipcad.review.domain.UserRole;
import com.shipcad.review.dto.ApiDtos.DrawingRequest;
import com.shipcad.review.dto.ApiDtos.IssueUpdateRequest;
import com.shipcad.review.dto.ApiDtos.OcrRegion;
import com.shipcad.review.dto.ApiDtos.OcrResponse;
import com.shipcad.review.dto.ApiDtos.ProjectRequest;
import com.shipcad.review.dto.ApiDtos.ReviewTaskRequest;
import com.shipcad.review.dto.ApiDtos.VersionCompareResponse;
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
import com.shipcad.review.repo.ReviewTaskStepRepository;
import com.shipcad.review.storage.ObjectStorageService;
import com.shipcad.review.storage.StoredObject;
import jakarta.transaction.Transactional;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ReviewPlatformService implements ReviewTaskRunner {
    private static final int DEFAULT_RENDER_WIDTH = 1600;
    private static final int DEFAULT_RENDER_HEIGHT = 1200;

    private final ProjectRepository projects;
    private final DrawingRepository drawings;
    private final DrawingVersionRepository versions;
    private final ParsedEntityRepository entities;
    private final ReviewRuleRepository rules;
    private final ReviewTaskRepository tasks;
    private final ReviewTaskStepRepository taskSteps;
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
    private final VersionCompareService versionCompareService;
    private final AiGateway aiGateway;
    private final ProjectAccessService projectAccess;
    private final AuditService audit;
    private final ObjectMapper mapper;
    private final ReviewTaskQueue reviewTaskQueue;
    private final ObjectStorageService objectStorage;

    public ReviewPlatformService(
            ProjectRepository projects,
            DrawingRepository drawings,
            DrawingVersionRepository versions,
            ParsedEntityRepository entities,
            ReviewRuleRepository rules,
            ReviewTaskRepository tasks,
            ReviewTaskStepRepository taskSteps,
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
            VersionCompareService versionCompareService,
            AiGateway aiGateway,
            ProjectAccessService projectAccess,
            AuditService audit,
            ObjectMapper mapper,
            ReviewTaskQueue reviewTaskQueue,
            ObjectStorageService objectStorage
    ) {
        this.projects = projects;
        this.drawings = drawings;
        this.versions = versions;
        this.entities = entities;
        this.rules = rules;
        this.tasks = tasks;
        this.taskSteps = taskSteps;
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
        this.versionCompareService = versionCompareService;
        this.aiGateway = aiGateway;
        this.projectAccess = projectAccess;
        this.audit = audit;
        this.mapper = mapper;
        this.reviewTaskQueue = reviewTaskQueue;
        this.objectStorage = objectStorage;
    }

    @Transactional
    public Project createProject(ProjectRequest request, AppUser actor) {
        Project project = new Project();
        project.id = Ids.next("project");
        project.name = request.name();
        project.shipNo = request.shipNo();
        project.owner = request.owner();
        project.description = request.description();
        project.createdAt = Ids.now();
        projects.save(project);
        projectAccess.addCreatorMembership(project.id, actor);
        audit.record(actor.username, "PROJECT_CREATE", "project", project.id, project);
        return project;
    }

    public Drawing createDrawing(DrawingRequest request, AppUser actor) {
        projectAccess.requireProject(actor, request.projectId());
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
        projectAccess.requireDrawing(actor, drawingId);
        String originalName = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();
        if (file.isEmpty() || !(originalName.endsWith(".dxf") || originalName.endsWith(".dwg"))) {
            throw new IllegalArgumentException("当前支持DXF或DWG文件，DWG解析需要安装LibreDWG");
        }
        if (file.getSize() > 20L * 1024 * 1024) {
            throw new IllegalArgumentException("文件超过20MB限制");
        }
        String id = Ids.next("version");
        String fileName = safeFileName(file.getOriginalFilename(), "drawing.dxf");
        String objectKey = "uploads/" + drawingId + "/" + id + "_" + fileName;
        StoredObject stored = objectStorage.storeMultipart(
                objectKey,
                file,
                contentTypeForFileName(fileName, "application/octet-stream")
        );

        DrawingVersion version = new DrawingVersion();
        version.id = id;
        version.drawingId = drawingId;
        version.versionNo = versionNo;
        version.fileName = fileName;
        version.filePath = stored.localPath().toString();
        version.storageMode = stored.storageMode();
        version.fileObjectKey = stored.key();
        version.fileSha256 = sha256(stored.localPath());
        version.uploadedBy = actor.username;
        version.uploadedAt = Ids.now();
        version.parseStatus = "PENDING";
        version.parseSummaryJson = "{}";
        versions.save(version);
        audit.record(actor.username, "VERSION_UPLOAD", "version", id, Map.of(
                "fileName", version.fileName,
                "storageMode", version.storageMode,
                "objectKey", version.fileObjectKey,
                "sha256", version.fileSha256
        ));
        return version;
    }

    @Transactional
    public DrawingVersion parseVersion(String versionId, AppUser actor) {
        DrawingVersion version = projectAccess.requireVersion(actor, versionId);
        WorkerParseResponse parsed = worker.parse(localFileForVersion(version));
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
        DrawingVersion version = projectAccess.requireVersion(actor, versionId);
        String objectKey = renderedImageKey(versionId);
        if (!force && objectStorage.exists(objectKey)) {
            return objectStorage.resolveLocalPath(objectKey);
        }
        byte[] png = worker.render(localFileForVersion(version), DEFAULT_RENDER_WIDTH, DEFAULT_RENDER_HEIGHT);
        if (png == null || png.length == 0) {
            throw new IllegalStateException("CAD Worker returned an empty rendered image");
        }
        StoredObject stored = objectStorage.storeBytes(objectKey, png, "image/png");
        audit.record(actor.username, "VERSION_RENDER", "version", versionId, Map.of(
                "imagePath", stored.localPath().toString(),
                "storageMode", stored.storageMode(),
                "objectKey", stored.key(),
                "width", DEFAULT_RENDER_WIDTH,
                "height", DEFAULT_RENDER_HEIGHT
        ));
        return stored.localPath();
    }

    @Transactional
    public List<ReviewEvidence> runVisionDetection(String versionId, MultipartFile file, double confidence, AppUser actor) throws IOException {
        projectAccess.requireVersion(actor, versionId);
        validateVisionConfidence(confidence);
        Path image = storeVisionImage(versionId, file);
        return runVisionDetectionOnImage(versionId, null, image, confidence, actor, "VISION_DETECT", "uploaded-image");
    }

    @Transactional
    public List<ReviewEvidence> runVisionDetectionFromRenderedImage(String versionId, double confidence, boolean forceRender, AppUser actor) throws IOException {
        projectAccess.requireVersion(actor, versionId);
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
        projectAccess.requireVersion(actor, versionId);
        validateOcrConfidence(confidence);
        Path image = storeOcrImage(versionId, file);
        return runOcrRecognitionOnImage(versionId, null, image, confidence, actor, "OCR_RECOGNIZE", "uploaded-image");
    }

    @Transactional
    public List<ReviewEvidence> runOcrRecognitionFromRenderedImage(String versionId, double confidence, boolean forceRender, AppUser actor) throws IOException {
        projectAccess.requireVersion(actor, versionId);
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

    public List<ReviewTask> listReviewTasks(String versionId, AppUser actor) {
        return attachTaskSteps(projectAccess.listTasks(actor, versionId));
    }

    public ReviewTask getReviewTask(String taskId, AppUser actor) {
        return attachTaskSteps(projectAccess.requireTask(actor, taskId));
    }

    public List<ReviewTaskStep> listReviewTaskSteps(String taskId, AppUser actor) {
        projectAccess.requireTask(actor, taskId);
        return taskSteps.findByTaskIdOrderByStepOrderAsc(taskId);
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
        projectAccess.requireVersion(actor, versionId);
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
        task.stage = "QUEUED";
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

        try {
            reviewTaskQueue.enqueue(task.id, actor.username);
        } catch (RuntimeException exception) {
            task.status = "FAILED";
            task.stage = "QUEUE_FAILED";
            task.finishedAt = Ids.now();
            task.errorMessage = "审查任务入队失败: " + messageOf(exception);
            tasks.save(task);
            audit.record(actor.username, "REVIEW_QUEUE_FAILED", "task", task.id, Map.of(
                    "versionId", versionId,
                    "error", task.errorMessage
            ));
            throw exception;
        }
        return task;
    }

    public ReviewTask retryReviewTask(String taskId, AppUser actor) {
        ReviewTask oldTask = projectAccess.requireTask(actor, taskId);
        if (!"FAILED".equals(oldTask.status)) {
            throw new IllegalArgumentException("Only FAILED review tasks can be retried.");
        }
        ReviewTask retry = createReviewTask(
                oldTask.versionId,
                bool(oldTask.autoVision),
                bool(oldTask.autoOcr),
                bool(oldTask.forceRender),
                confidenceOrDefault(oldTask.visionConfidence, 0.25),
                confidenceOrDefault(oldTask.ocrConfidence, 0.5),
                actor
        );
        audit.record(actor.username, "REVIEW_RETRY", "task", retry.id, Map.of(
                "sourceTaskId", oldTask.id,
                "versionId", oldTask.versionId
        ));
        return retry;
    }

    @Override
    public void runQueuedReviewTask(String taskId, String actorUsername) {
        ReviewTask task = tasks.findById(taskId).orElseThrow(() -> new IllegalArgumentException("审查任务不存在"));
        if ("FINISHED".equals(task.status) || "FAILED".equals(task.status)) {
            return;
        }
        task.status = "RUNNING";
        task.stage = "PARSING";
        task.startedAt = Ids.now();
        task.errorMessage = "";
        tasks.save(task);

        try {
            DrawingVersion version = versions.findById(task.versionId).orElseThrow(() -> new IllegalArgumentException("版本不存在"));
            if (!"SUCCESS".equals(version.parseStatus)) {
                ReviewTaskStep parseStep = startTaskStep(task, 10, "PARSE", "CAD结构化解析");
                try {
                    version = parseVersion(task.versionId, systemActor(actorUsername));
                    WorkerSummary parsedSummary = fromJson(version.parseSummaryJson, WorkerSummary.class);
                    completeTaskStep(parseStep, "解析完成", Map.of(
                            "parseStatus", version.parseStatus,
                            "entityCount", parsedSummary.entityCount()
                    ));
                } catch (Exception exception) {
                    failTaskStep(parseStep, messageOf(exception));
                    throw exception;
                }
            } else {
                skipTaskStep(task, 10, "PARSE", "CAD结构化解析", "版本已解析，复用结构化结果", Map.of("parseStatus", version.parseStatus));
            }
            collectAutomaticEvidenceWithSteps(task, systemActor(actorUsername));

            task.stage = "RULE_REVIEWING";
            tasks.save(task);
            ReviewTaskStep ruleStep = startTaskStep(task, 50, "RULES", "确定性规则审查");
            WorkerSummary summary = fromJson(version.parseSummaryJson, WorkerSummary.class);
            List<ParsedEntity> parsedEntities = entities.findByVersionId(task.versionId);
            List<ReviewEvidence> versionEvidences = evidences.findByVersionId(task.versionId).stream()
                    .filter(evidence -> evidence.issueId == null || evidence.issueId.isBlank())
                    .filter(evidence -> evidence.taskId == null || evidence.taskId.isBlank() || task.id.equals(evidence.taskId))
                    .toList();
            List<ReviewRule> enabledRules = rules.findByEnabledTrue();
            List<ReviewIssue> generated;
            try {
                generated = ruleEngine.run(
                        task.id,
                        version,
                        summary,
                        parsedEntities,
                        enabledRules,
                        knowledgeClauses.findAll(),
                        versionEvidences
                );
                issues.saveAll(generated);
                evidences.saveAll(generated.stream()
                        .flatMap(issue -> safeEvidences(issue).stream())
                        .toList());
                completeTaskStep(ruleStep, "规则审查完成", Map.of(
                        "ruleCount", enabledRules.size(),
                        "evidenceInputCount", versionEvidences.size(),
                        "issueCount", generated.size()
                ));
            } catch (Exception exception) {
                failTaskStep(ruleStep, messageOf(exception));
                throw exception;
            }

            task.status = "FINISHED";
            task.stage = "FINISHED";
            task.finishedAt = Ids.now();
            task.issueCount = generated.size();
            tasks.save(task);
            audit.record(actorUsername, "REVIEW_FINISHED", "task", task.id, Map.of("versionId", task.versionId, "issueCount", generated.size()));
        } catch (Exception exception) {
            String message = messageOf(exception);
            versions.findById(task.versionId).ifPresent(version -> {
                if (!"SUCCESS".equals(version.parseStatus)) {
                    version.parseStatus = "FAILED";
                    versions.save(version);
                }
            });
            task.status = "FAILED";
            task.stage = "FAILED";
            task.finishedAt = Ids.now();
            task.errorMessage = message;
            tasks.save(task);
            audit.record(actorUsername, "REVIEW_FAILED", "task", task.id, Map.of("versionId", task.versionId, "error", message));
        }
    }

    private void collectAutomaticEvidenceWithSteps(ReviewTask task, AppUser actor) throws Exception {
        if (!bool(task.autoVision) && !bool(task.autoOcr)) {
            skipTaskStep(task, 20, "RENDER", "版本渲染图生成", "未启用自动 Vision/OCR，跳过渲染", Map.of("autoVision", false, "autoOcr", false));
            skipTaskStep(task, 30, "VISION", "YOLOv8视觉识别", "未启用自动视觉证据", Map.of("autoVision", false));
            skipTaskStep(task, 40, "OCR", "OCR文字识别", "未启用自动OCR证据", Map.of("autoOcr", false));
            return;
        }
        task.stage = "RENDERING";
        tasks.save(task);
        ReviewTaskStep renderStep = startTaskStep(task, 20, "RENDER", "版本渲染图生成");
        Path image;
        try {
            image = renderVersionImage(task.versionId, bool(task.forceRender), actor);
            completeTaskStep(renderStep, "渲染图已生成", Map.of(
                    "imagePath", image.toString(),
                    "forceRender", bool(task.forceRender)
            ));
        } catch (Exception exception) {
            failTaskStep(renderStep, messageOf(exception));
            throw exception;
        }

        if (bool(task.autoVision)) {
            task.stage = "VISION_DETECTING";
            tasks.save(task);
            ReviewTaskStep visionStep = startTaskStep(task, 30, "VISION", "YOLOv8视觉识别");
            try {
                List<ReviewEvidence> generated = runVisionDetectionOnImage(
                        task.versionId,
                        task.id,
                        image,
                        confidenceOrDefault(task.visionConfidence, 0.25),
                        actor,
                        "REVIEW_AUTO_VISION_DETECT",
                        "review-rendered-version-image"
                );
                completeTaskStep(visionStep, "视觉证据采集完成", Map.of(
                        "evidenceCount", generated.size(),
                        "confidence", confidenceOrDefault(task.visionConfidence, 0.25)
                ));
            } catch (Exception exception) {
                failTaskStep(visionStep, messageOf(exception));
                throw exception;
            }
        } else {
            skipTaskStep(task, 30, "VISION", "YOLOv8视觉识别", "未启用自动视觉证据", Map.of("autoVision", false));
        }

        if (bool(task.autoOcr)) {
            task.stage = "OCR_RECOGNIZING";
            tasks.save(task);
            ReviewTaskStep ocrStep = startTaskStep(task, 40, "OCR", "OCR文字识别");
            try {
                List<ReviewEvidence> generated = runOcrRecognitionOnImage(
                        task.versionId,
                        task.id,
                        image,
                        confidenceOrDefault(task.ocrConfidence, 0.5),
                        actor,
                        "REVIEW_AUTO_OCR_RECOGNIZE",
                        "review-rendered-version-image"
                );
                completeTaskStep(ocrStep, "OCR证据采集完成", Map.of(
                        "evidenceCount", generated.size(),
                        "confidence", confidenceOrDefault(task.ocrConfidence, 0.5)
                ));
            } catch (Exception exception) {
                failTaskStep(ocrStep, messageOf(exception));
                throw exception;
            }
        } else {
            skipTaskStep(task, 40, "OCR", "OCR文字识别", "未启用自动OCR证据", Map.of("autoOcr", false));
        }
    }

    private List<ReviewTask> attachTaskSteps(List<ReviewTask> source) {
        if (source.isEmpty()) {
            return source;
        }
        List<String> taskIds = source.stream().map(task -> task.id).toList();
        Map<String, List<ReviewTaskStep>> stepsByTask = taskSteps.findByTaskIdInOrderByTaskIdAscStepOrderAsc(taskIds)
                .stream()
                .collect(Collectors.groupingBy(step -> step.taskId));
        source.forEach(task -> task.steps = stepsByTask.getOrDefault(task.id, List.of()));
        return source;
    }

    private ReviewTask attachTaskSteps(ReviewTask task) {
        task.steps = taskSteps.findByTaskIdOrderByStepOrderAsc(task.id);
        return task;
    }

    private ReviewTaskStep startTaskStep(ReviewTask task, int stepOrder, String stepCode, String stepName) {
        ReviewTaskStep step = new ReviewTaskStep();
        step.id = Ids.next("taskstep");
        step.taskId = task.id;
        step.stepOrder = stepOrder;
        step.stepCode = stepCode;
        step.stepName = stepName;
        step.status = "RUNNING";
        step.startedAt = Ids.now();
        step.message = "";
        step.detailJson = "{}";
        return taskSteps.save(step);
    }

    private void skipTaskStep(ReviewTask task, int stepOrder, String stepCode, String stepName, String message, Object detail) {
        ReviewTaskStep step = new ReviewTaskStep();
        Instant now = Ids.now();
        step.id = Ids.next("taskstep");
        step.taskId = task.id;
        step.stepOrder = stepOrder;
        step.stepCode = stepCode;
        step.stepName = stepName;
        step.status = "SKIPPED";
        step.startedAt = now;
        step.finishedAt = now;
        step.message = message;
        step.detailJson = detail == null ? "{}" : toJson(detail);
        taskSteps.save(step);
    }

    private void completeTaskStep(ReviewTaskStep step, String message, Object detail) {
        step.status = "SUCCESS";
        step.finishedAt = Ids.now();
        step.message = message;
        step.detailJson = detail == null ? "{}" : toJson(detail);
        taskSteps.save(step);
    }

    private void failTaskStep(ReviewTaskStep step, String message) {
        step.status = "FAILED";
        step.finishedAt = Ids.now();
        step.message = message;
        step.detailJson = toJson(Map.of("error", message));
        taskSteps.save(step);
    }

    public List<ReviewIssue> listIssues(String taskId, String versionId, AppUser actor) {
        return attachEvidence(projectAccess.listIssues(actor, taskId, versionId));
    }

    public List<ReviewEvidence> listIssueEvidence(String issueId, AppUser actor) {
        projectAccess.requireIssue(actor, issueId);
        return evidences.findByIssueId(issueId);
    }

    public List<RemediationRecord> listIssueRemediations(String issueId, AppUser actor) {
        projectAccess.requireIssue(actor, issueId);
        return remediations.findByIssueIdOrderByCreatedAtAsc(issueId);
    }

    public List<ReviewEvidence> listVersionEvidence(String versionId, EvidenceType type, AppUser actor) {
        projectAccess.requireVersion(actor, versionId);
        List<ReviewEvidence> source = evidences.findByVersionId(versionId);
        if (type == null) {
            return source;
        }
        return source.stream()
                .filter(evidence -> type.equals(evidence.evidenceType))
                .toList();
    }

    public com.shipcad.review.domain.AiExplanation explainIssue(String issueId, AppUser actor) {
        ReviewIssue issue = projectAccess.requireIssue(actor, issueId);
        return aiGateway.explain(attachEvidence(issue));
    }

    public ReviewIssue updateIssue(String issueId, IssueUpdateRequest request, AppUser actor) {
        ReviewIssue issue = projectAccess.requireIssue(actor, issueId);
        IssueStatus fromStatus = issue.status;
        String fromAssignee = issue.assignee;
        validateReportReference(request.reportId(), issue, actor);
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
        record.taskId = issue.taskId;
        record.versionId = issue.versionId;
        record.operator = actor.username;
        record.action = remediationAction(fromStatus, issue.status, fromAssignee, issue.assignee);
        record.fromStatus = fromStatus == null ? "" : fromStatus.name();
        record.toStatus = issue.status == null ? "" : issue.status.name();
        record.assignee = issue.assignee;
        record.reportId = request.reportId();
        record.note = request.note();
        record.createdAt = Ids.now();
        remediations.save(record);
        audit.record(actor.username, "ISSUE_UPDATE", "issue", issueId, request);
        return attachEvidence(issue);
    }

    private void validateReportReference(String reportId, ReviewIssue issue, AppUser actor) {
        if (reportId == null || reportId.isBlank()) {
            return;
        }
        ReportDocument report = projectAccess.requireReport(actor, reportId);
        if (issue.taskId == null || !issue.taskId.equals(report.taskId)) {
            throw new IllegalArgumentException("报告不属于当前问题所在审查任务");
        }
    }

    private String remediationAction(IssueStatus fromStatus, IssueStatus toStatus, String fromAssignee, String toAssignee) {
        if (toStatus != null && toStatus != fromStatus) {
            return switch (toStatus) {
                case IN_PROGRESS -> "START_REMEDIATION";
                case READY_FOR_REVIEW -> "SUBMIT_FOR_REVIEW";
                case CLOSED -> "CLOSE";
                case OPEN -> fromStatus == IssueStatus.CLOSED ? "REOPEN" : "MARK_OPEN";
            };
        }
        if (!java.util.Objects.equals(fromAssignee, toAssignee)) {
            return "ASSIGN";
        }
        return "COMMENT";
    }

    public ReportDocument createReport(String taskId, AppUser actor) {
        ReviewTask task = projectAccess.requireTask(actor, taskId);
        if (!"FINISHED".equals(task.status)) {
            throw new IllegalArgumentException("审查任务尚未完成，暂不能生成报告");
        }
        DrawingVersion version = versions.findById(task.versionId).orElseThrow();
        Drawing drawing = drawings.findById(version.drawingId).orElseThrow();
        Project project = projects.findById(drawing.projectId).orElseThrow();
        List<ReviewIssue> taskIssues = attachEvidence(issues.findByTaskId(taskId));
        WorkerSummary summary = fromJson(version.parseSummaryJson, WorkerSummary.class);
        List<ParsedEntity> parsedEntities = entities.findByVersionId(version.id);
        String content = reportBuilder.build(project, drawing, version, summary, taskIssues, parsedEntities);
        ReportDocument report = new ReportDocument();
        report.id = Ids.next("report");
        report.taskId = taskId;
        report.versionId = version.id;
        report.content = content;
        StoredObject stored = storeReportContent(report, content);
        report.storageMode = stored.storageMode();
        report.contentObjectKey = stored.key();
        report.contentPath = stored.localPath().toString();
        report.contentSizeBytes = stored.size();
        report.createdAt = Ids.now();
        reports.save(report);
        audit.record(actor.username, "REPORT_CREATE", "report", report.id, Map.of(
                "taskId", taskId,
                "storageMode", report.storageMode,
                "objectKey", report.contentObjectKey,
                "sizeBytes", report.contentSizeBytes
        ));
        return report;
    }

    public VersionCompareResponse compareVersions(String leftId, String rightId, AppUser actor) {
        DrawingVersion leftVersion = projectAccess.requireVersion(actor, leftId);
        DrawingVersion rightVersion = projectAccess.requireVersion(actor, rightId);
        WorkerSummary left = fromJson(leftVersion.parseSummaryJson, WorkerSummary.class);
        WorkerSummary right = fromJson(rightVersion.parseSummaryJson, WorkerSummary.class);
        return versionCompareService.compare(leftVersion, left, rightVersion, right);
    }

    public ReportDocument getReport(String reportId, AppUser actor) {
        return projectAccess.requireReport(actor, reportId);
    }

    public String reportContent(ReportDocument report) {
        if (report.contentObjectKey != null && !report.contentObjectKey.isBlank()) {
            try {
                return Files.readString(objectStorage.resolveLocalPath(report.contentObjectKey), StandardCharsets.UTF_8);
            } catch (IOException | RuntimeException exception) {
                throw new IllegalStateException("璇诲彇鎶ュ憡瀵硅薄鏂囦欢澶辫触", exception);
            }
        }
        return report.content == null ? "" : report.content;
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

    private String renderedImageKey(String versionId) {
        return "rendered/" + versionId + "/render.png";
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

    private String messageOf(Exception exception) {
        return exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
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
        StoredObject stored = objectStorage.storeMultipart(
                folderName + "/" + versionId + "/" + Ids.next(idPrefix) + "_" + fileName,
                file,
                contentTypeForFileName(fileName, "application/octet-stream")
        );
        return stored.localPath();
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

    private String contentTypeForFileName(String fileName, String fallback) {
        String lower = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".dxf")) {
            return "application/dxf";
        }
        if (lower.endsWith(".png")) {
            return "image/png";
        }
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        return fallback;
    }

    private Path localFileForVersion(DrawingVersion version) {
        if (version.fileObjectKey != null && !version.fileObjectKey.isBlank()) {
            try {
                return objectStorage.resolveLocalPath(version.fileObjectKey);
            } catch (IOException exception) {
                throw new IllegalStateException("读取对象存储文件失败", exception);
            }
        }
        if (version.filePath == null || version.filePath.isBlank()) {
            throw new IllegalArgumentException("图纸文件路径缺失");
        }
        return Path.of(version.filePath).toAbsolutePath().normalize();
    }

    private StoredObject storeReportContent(ReportDocument report, String content) {
        try {
            return objectStorage.storeBytes(
                    "reports/" + report.versionId + "/" + report.taskId + "/" + report.id + ".md",
                    content.getBytes(StandardCharsets.UTF_8),
                    "text/markdown;charset=UTF-8"
            );
        } catch (IOException exception) {
            throw new IllegalStateException("淇濆瓨鎶ュ憡瀵硅薄鏂囦欢澶辫触", exception);
        }
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
        user.id = "internal_review_worker";
        user.username = username;
        user.role = UserRole.ADMIN;
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
