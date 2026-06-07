package com.shipcad.review.dto;

import com.shipcad.review.domain.IssueStatus;
import com.shipcad.review.domain.UserRole;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class ApiDtos {
    private ApiDtos() {
    }

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {
    }

    public record LoginResponse(String token, Instant expiresAt, UserView user, List<String> permissions) {
    }

    public record UserView(String id, String username, String displayName, String role) {
    }

    public record AccessView(UserView user, List<String> permissions, Instant sessionExpiresAt) {
    }

    public record CreateUserRequest(
            @NotBlank String username,
            @NotBlank String displayName,
            UserRole role,
            @NotBlank String password,
            Boolean enabled
    ) {
    }

    public record UpdateUserRequest(String displayName, UserRole role, Boolean enabled) {
    }

    public record ResetPasswordRequest(@NotBlank String newPassword) {
    }

    public record ChangePasswordRequest(@NotBlank String currentPassword, @NotBlank String newPassword) {
    }

    public record ManagedUserView(
            String id,
            String username,
            String displayName,
            String role,
            boolean enabled,
            Instant createdAt,
            Instant updatedAt,
            Instant passwordChangedAt,
            Instant lastLoginAt
    ) {
    }

    public record AuditLogView(
            String id,
            String actor,
            String action,
            String targetType,
            String targetId,
            String detailJson,
            Instant createdAt
    ) {
    }

    public record AuditLogPage(List<AuditLogView> items, long total, int page, int size, int totalPages) {
    }

    public record ProjectRequest(@NotBlank String name, String shipNo, String owner, String description) {
    }

    public record DrawingRequest(@NotBlank String projectId, @NotBlank String drawingNo, @NotBlank String title, String discipline) {
    }

    public record ReviewTaskRequest(
            @NotBlank String versionId,
            Boolean autoVision,
            Boolean autoOcr,
            Boolean forceRender,
            Double visionConfidence,
            Double ocrConfidence
    ) {
    }

    public record IssueUpdateRequest(IssueStatus status, String assignee, String note, String reportId) {
    }

    public record ReportRequest(@NotBlank String taskId) {
    }

    public record WorkerParseResponse(List<WorkerEntity> entities, WorkerSummary summary) {
    }

    public record WorkerEntity(String entityType, String layer, String text, String blockName, Double x, Double y, Map<String, Object> geometry) {
    }

    public record VisionDetectionResponse(List<VisionDetection> detections, Integer imageWidth, Integer imageHeight, String engine) {
    }

    public record VisionDetection(Integer classId, String className, Double confidence, List<Double> xyxy) {
    }

    public record OcrResponse(List<OcrRegion> regions, Integer imageWidth, Integer imageHeight, String engine, String language) {
    }

    public record OcrRegion(String text, Double confidence, List<Double> xyxy, String language) {
    }

    public record EntityView(String id, String versionId, String entityType, String layerName, String textValue, String blockName, Double x, Double y, Map<String, Object> geometry) {
    }

    public record VersionCompareSide(
            String id,
            String versionNo,
            String fileName,
            String parseStatus,
            int entityCount,
            int layerCount,
            int textCount,
            int blockCount
    ) {
    }

    public record VersionCountDelta(String name, int leftCount, int rightCount, int delta) {
    }

    public record VersionCompareResponse(
            VersionCompareSide left,
            VersionCompareSide right,
            int entityCountDelta,
            List<String> addedLayers,
            List<String> removedLayers,
            List<String> addedEmptyLayers,
            List<String> removedEmptyLayers,
            List<String> addedBlocks,
            List<String> removedBlocks,
            List<String> addedTexts,
            List<String> removedTexts,
            List<VersionCountDelta> layerDeltas,
            List<VersionCountDelta> typeDeltas,
            List<String> riskHints,
            List<String> reviewFocus,
            String summary
    ) {
    }

    public record WorkerSummary(
            int entityCount,
            Map<String, Integer> typeCounts,
            Map<String, Integer> layerCounts,
            List<String> layers,
            List<String> emptyLayers,
            List<String> texts,
            List<String> blocks,
            Map<String, Double> bounds,
            String parser,
            String ezdxfVersion
    ) {
    }
}
