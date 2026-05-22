package com.shipcad.review.api;

import com.shipcad.review.domain.DrawingVersion;
import com.shipcad.review.domain.ParsedEntity;
import com.shipcad.review.dto.ApiDtos.EntityView;
import com.shipcad.review.repo.DrawingVersionRepository;
import com.shipcad.review.repo.ParsedEntityRepository;
import com.shipcad.review.service.AuthService;
import com.shipcad.review.service.ReviewPlatformService;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
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
    public List<EntityView> entities(@RequestHeader("Authorization") String authorization, @org.springframework.web.bind.annotation.PathVariable String versionId) {
        user(authorization);
        return entities.findByVersionId(versionId).stream().map(this::view).toList();
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
