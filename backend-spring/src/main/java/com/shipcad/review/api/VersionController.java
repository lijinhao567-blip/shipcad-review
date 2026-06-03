package com.shipcad.review.api;

import com.shipcad.review.domain.DrawingVersion;
import com.shipcad.review.domain.EvidenceType;
import com.shipcad.review.domain.ParsedEntity;
import com.shipcad.review.domain.ReviewEvidence;
import com.shipcad.review.dto.ApiDtos.EntityView;
import com.shipcad.review.repo.DrawingVersionRepository;
import com.shipcad.review.repo.ParsedEntityRepository;
import com.shipcad.review.service.AuthService;
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

    public VersionController(AuthService auth, DrawingVersionRepository versions, ParsedEntityRepository entities, ReviewPlatformService platform) {
        super(auth);
        this.versions = versions;
        this.entities = entities;
        this.platform = platform;
    }

    @GetMapping
    public List<DrawingVersion> versions(@RequestHeader("Authorization") String authorization, @RequestParam(required = false) String drawingId) {
        user(authorization);
        return drawingId == null || drawingId.isBlank() ? versions.findAll() : versions.findByDrawingId(drawingId);
    }

    @PostMapping("/upload")
    public DrawingVersion upload(
            @RequestHeader("Authorization") String authorization,
            @RequestParam String drawingId,
            @RequestParam String versionNo,
            @RequestParam MultipartFile file
    ) throws IOException {
        return platform.uploadVersion(drawingId, versionNo, file, user(authorization));
    }

    @PostMapping("/parse")
    public DrawingVersion parse(@RequestHeader("Authorization") String authorization, @RequestParam String versionId) {
        return platform.parseVersion(versionId, user(authorization));
    }

    @GetMapping("/compare")
    public Map<String, Object> compare(@RequestHeader("Authorization") String authorization, @RequestParam String leftId, @RequestParam String rightId) {
        user(authorization);
        return platform.compareVersions(leftId, rightId);
    }

    @GetMapping("/{versionId}/entities")
    public List<EntityView> entities(@RequestHeader("Authorization") String authorization, @PathVariable String versionId) {
        user(authorization);
        return entities.findByVersionId(versionId).stream().map(this::view).toList();
    }

    @PostMapping("/{versionId}/vision-detect")
    public List<ReviewEvidence> visionDetect(
            @RequestHeader("Authorization") String authorization,
            @PathVariable String versionId,
            @RequestParam MultipartFile file,
            @RequestParam(defaultValue = "0.25") double confidence
    ) throws IOException {
        return platform.runVisionDetection(versionId, file, confidence, user(authorization));
    }

    @PostMapping("/{versionId}/ocr-recognize")
    public List<ReviewEvidence> ocrRecognize(
            @RequestHeader("Authorization") String authorization,
            @PathVariable String versionId,
            @RequestParam MultipartFile file,
            @RequestParam(defaultValue = "0.5") double confidence
    ) throws IOException {
        return platform.runOcrRecognition(versionId, file, confidence, user(authorization));
    }

    @GetMapping("/{versionId}/evidences")
    public List<ReviewEvidence> evidences(
            @RequestHeader("Authorization") String authorization,
            @PathVariable String versionId,
            @RequestParam(required = false) EvidenceType type
    ) {
        user(authorization);
        return platform.listVersionEvidence(versionId, type);
    }

    @GetMapping("/{versionId}/file")
    public ResponseEntity<Resource> file(@RequestHeader("Authorization") String authorization, @PathVariable String versionId) throws MalformedURLException {
        user(authorization);
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
