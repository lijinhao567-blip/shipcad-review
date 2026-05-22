package com.shipcad.review.dto;

import com.shipcad.review.domain.IssueStatus;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Map;

public final class ApiDtos {
    private ApiDtos() {
    }

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {
    }

    public record LoginResponse(String token, UserView user) {
    }

    public record UserView(String id, String username, String displayName, String role) {
    }

    public record ProjectRequest(@NotBlank String name, String shipNo, String owner, String description) {
    }

    public record DrawingRequest(@NotBlank String projectId, @NotBlank String drawingNo, @NotBlank String title, String discipline) {
    }

    public record ReviewTaskRequest(@NotBlank String versionId) {
    }

    public record IssueUpdateRequest(IssueStatus status, String assignee, String note) {
    }

    public record ReportRequest(@NotBlank String taskId) {
    }

    public record WorkerParseResponse(List<WorkerEntity> entities, WorkerSummary summary) {
    }

    public record WorkerEntity(String entityType, String layer, String text, String blockName, Double x, Double y, Map<String, Object> geometry) {
    }

    public record EntityView(String id, String versionId, String entityType, String layerName, String textValue, String blockName, Double x, Double y, Map<String, Object> geometry) {
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
