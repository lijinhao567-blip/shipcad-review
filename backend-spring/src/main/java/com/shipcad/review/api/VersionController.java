package com.shipcad.review.api;

import com.shipcad.review.domain.DrawingVersion;
import com.shipcad.review.domain.EvidenceType;
import com.shipcad.review.domain.ParsedEntity;
import com.shipcad.review.domain.Permission;
import com.shipcad.review.domain.ReviewEvidence;
import com.shipcad.review.dto.ApiDtos.EntityView;
import com.shipcad.review.dto.ApiDtos.VersionCompareResponse;
import com.shipcad.review.repo.DrawingVersionRepository;
import com.shipcad.review.repo.ParsedEntityRepository;
import com.shipcad.review.service.AuthService;
import com.shipcad.review.service.AuthorizationService;
import com.shipcad.review.service.ProjectAccessService;
import com.shipcad.review.service.ReviewPlatformService;
import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/versions")
public class VersionController extends BaseController {
    private final DrawingVersionRepository versions;
    private final ParsedEntityRepository entities;
    private final ReviewPlatformService platform;
    private final AuthorizationService access;
    private final ProjectAccessService projectAccess;

    public VersionController(AuthService auth, DrawingVersionRepository versions, ParsedEntityRepository entities,
                             ReviewPlatformService platform, AuthorizationService access,
                             ProjectAccessService projectAccess) {
        super(auth);
        this.versions = versions;
        this.entities = entities;
        this.platform = platform;
        this.access = access;
        this.projectAccess = projectAccess;
    }

    @GetMapping
    public List<DrawingVersion> versions(@RequestHeader("Authorization") String authorization, @RequestParam(required = false) String drawingId) {
        return projectAccess.listVersions(user(authorization), drawingId);
    }

    @PostMapping("/upload")
    public DrawingVersion upload(
            @RequestHeader("Authorization") String authorization,
            @RequestParam String drawingId,
            @RequestParam String versionNo,
            @RequestParam MultipartFile file
    ) throws IOException {
        var actor = user(authorization);
        access.require(actor, Permission.VERSION_UPLOAD);
        return platform.uploadVersion(drawingId, versionNo, file, actor);
    }

    @PostMapping("/parse")
    public DrawingVersion parse(@RequestHeader("Authorization") String authorization, @RequestParam String versionId) {
        var actor = user(authorization);
        access.require(actor, Permission.EVIDENCE_COLLECT);
        return platform.parseVersion(versionId, actor);
    }

    @GetMapping("/compare")
    public VersionCompareResponse compare(@RequestHeader("Authorization") String authorization, @RequestParam String leftId, @RequestParam String rightId) {
        return platform.compareVersions(leftId, rightId, user(authorization));
    }

    @GetMapping("/{versionId}/entities")
    public List<EntityView> entities(@RequestHeader("Authorization") String authorization, @PathVariable String versionId) {
        projectAccess.requireVersion(user(authorization), versionId);
        return entities.findByVersionId(versionId).stream().map(this::view).toList();
    }

    @PostMapping("/{versionId}/vision-detect")
    public List<ReviewEvidence> visionDetect(
            @RequestHeader("Authorization") String authorization,
            @PathVariable String versionId,
            @RequestParam MultipartFile file,
            @RequestParam(defaultValue = "0.25") double confidence
    ) throws IOException {
        var actor = user(authorization);
        access.require(actor, Permission.EVIDENCE_COLLECT);
        return platform.runVisionDetection(versionId, file, confidence, actor);
    }

    @PostMapping("/{versionId}/vision-detect-rendered")
    public List<ReviewEvidence> visionDetectRendered(
            @RequestHeader("Authorization") String authorization,
            @PathVariable String versionId,
            @RequestParam(defaultValue = "0.25") double confidence,
            @RequestParam(defaultValue = "false") boolean forceRender
    ) throws IOException {
        var actor = user(authorization);
        access.require(actor, Permission.EVIDENCE_COLLECT);
        return platform.runVisionDetectionFromRenderedImage(versionId, confidence, forceRender, actor);
    }

    @PostMapping("/{versionId}/ocr-recognize")
    public List<ReviewEvidence> ocrRecognize(
            @RequestHeader("Authorization") String authorization,
            @PathVariable String versionId,
            @RequestParam MultipartFile file,
            @RequestParam(defaultValue = "0.5") double confidence
    ) throws IOException {
        var actor = user(authorization);
        access.require(actor, Permission.EVIDENCE_COLLECT);
        return platform.runOcrRecognition(versionId, file, confidence, actor);
    }

    @PostMapping("/{versionId}/ocr-recognize-rendered")
    public List<ReviewEvidence> ocrRecognizeRendered(
            @RequestHeader("Authorization") String authorization,
            @PathVariable String versionId,
            @RequestParam(defaultValue = "0.5") double confidence,
            @RequestParam(defaultValue = "false") boolean forceRender
    ) throws IOException {
        var actor = user(authorization);
        access.require(actor, Permission.EVIDENCE_COLLECT);
        return platform.runOcrRecognitionFromRenderedImage(versionId, confidence, forceRender, actor);
    }

    @GetMapping("/{versionId}/evidences")
    public List<ReviewEvidence> evidences(
            @RequestHeader("Authorization") String authorization,
            @PathVariable String versionId,
            @RequestParam(required = false) EvidenceType type
    ) {
        return platform.listVersionEvidence(versionId, type, user(authorization));
    }

    @GetMapping("/{versionId}/rendered-image")
    public ResponseEntity<Resource> renderedImage(
            @RequestHeader("Authorization") String authorization,
            @PathVariable String versionId,
            @RequestParam(defaultValue = "false") boolean force
    ) throws IOException {
        var actor = user(authorization);
        access.require(actor, Permission.EVIDENCE_COLLECT);
        Path path = platform.renderVersionImage(versionId, force, actor);
        if (!Files.isRegularFile(path) || !Files.isReadable(path)) {
            throw new IllegalArgumentException("渲染图不可读取");
        }
        ContentDisposition disposition = ContentDisposition.inline()
                .filename(versionId + ".png", StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(new UrlResource(path.toUri()));
    }

    @GetMapping("/{versionId}/file")
    public ResponseEntity<Resource> file(@RequestHeader("Authorization") String authorization, @PathVariable String versionId) throws MalformedURLException {
        projectAccess.requireVersion(user(authorization), versionId);
        DrawingVersion version = versions.findById(versionId).orElseThrow(() -> new IllegalArgumentException("版本不存在"));
        if (version.filePath == null || version.filePath.isBlank()) {
            throw new IllegalArgumentException("图纸文件路径缺失");
        }
        Path path = Path.of(version.filePath).toAbsolutePath().normalize();
        if (!Files.isRegularFile(path) || !Files.isReadable(path)) {
            throw new IllegalArgumentException("图纸文件不可读取");
        }
        String fileName = version.fileName == null || version.fileName.isBlank() ? "drawing" : version.fileName;
        MediaType contentType = fileName.toLowerCase().endsWith(".dxf")
                ? MediaType.parseMediaType("application/dxf")
                : MediaType.APPLICATION_OCTET_STREAM;
        ContentDisposition disposition = ContentDisposition.inline()
                .filename(fileName, StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(contentType)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(new UrlResource(path.toUri()));
    }

    private EntityView view(ParsedEntity entity) {
        return new EntityView(
                entity.id,
                entity.versionId,
                entity.entityType,
                entity.layerName,
                entity.textValue,
                entity.blockName,
                entity.x,
                entity.y,
                platform.geometryOf(entity)
        );
    }
}
