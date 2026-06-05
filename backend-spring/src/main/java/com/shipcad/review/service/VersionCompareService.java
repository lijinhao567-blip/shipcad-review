package com.shipcad.review.service;

import com.shipcad.review.domain.DrawingVersion;
import com.shipcad.review.dto.ApiDtos.VersionCompareResponse;
import com.shipcad.review.dto.ApiDtos.VersionCompareSide;
import com.shipcad.review.dto.ApiDtos.VersionCountDelta;
import com.shipcad.review.dto.ApiDtos.WorkerSummary;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class VersionCompareService {
    public VersionCompareResponse compare(
            DrawingVersion leftVersion,
            WorkerSummary left,
            DrawingVersion rightVersion,
            WorkerSummary right
    ) {
        List<String> addedLayers = added(left.layers(), right.layers());
        List<String> removedLayers = added(right.layers(), left.layers());
        List<String> addedEmptyLayers = added(left.emptyLayers(), right.emptyLayers());
        List<String> removedEmptyLayers = added(right.emptyLayers(), left.emptyLayers());
        List<String> addedBlocks = added(left.blocks(), right.blocks());
        List<String> removedBlocks = added(right.blocks(), left.blocks());
        List<String> addedTexts = added(left.texts(), right.texts());
        List<String> removedTexts = added(right.texts(), left.texts());
        List<VersionCountDelta> layerDeltas = countDeltas(left.layerCounts(), right.layerCounts());
        List<VersionCountDelta> typeDeltas = countDeltas(left.typeCounts(), right.typeCounts());
        int entityCountDelta = right.entityCount() - left.entityCount();

        List<String> riskHints = riskHints(
                leftVersion,
                rightVersion,
                left,
                right,
                entityCountDelta,
                addedEmptyLayers,
                removedLayers,
                addedTexts,
                removedBlocks
        );
        List<String> reviewFocus = reviewFocus(addedLayers, removedLayers, addedBlocks, removedBlocks, addedTexts, removedTexts, entityCountDelta);

        return new VersionCompareResponse(
                side(leftVersion, left),
                side(rightVersion, right),
                entityCountDelta,
                addedLayers,
                removedLayers,
                addedEmptyLayers,
                removedEmptyLayers,
                addedBlocks,
                removedBlocks,
                addedTexts,
                removedTexts,
                layerDeltas,
                typeDeltas,
                riskHints,
                reviewFocus,
                summary(leftVersion, rightVersion, entityCountDelta, addedLayers, removedLayers, addedTexts, removedTexts, riskHints)
        );
    }

    private VersionCompareSide side(DrawingVersion version, WorkerSummary summary) {
        return new VersionCompareSide(
                version.id,
                safe(version.versionNo),
                safe(version.fileName),
                safe(version.parseStatus),
                summary.entityCount(),
                safeList(summary.layers()).size(),
                safeList(summary.texts()).size(),
                safeList(summary.blocks()).size()
        );
    }

    private List<String> added(List<String> previous, List<String> current) {
        Set<String> previousSet = new HashSet<>(safeList(previous));
        return safeList(current).stream()
                .filter(item -> !item.isBlank())
                .filter(item -> !previousSet.contains(item))
                .distinct()
                .sorted()
                .toList();
    }

    private List<VersionCountDelta> countDeltas(Map<String, Integer> left, Map<String, Integer> right) {
        Set<String> names = new HashSet<>();
        names.addAll(safeMap(left).keySet());
        names.addAll(safeMap(right).keySet());
        return names.stream()
                .filter(name -> !name.isBlank())
                .map(name -> new VersionCountDelta(
                        name,
                        safeMap(left).getOrDefault(name, 0),
                        safeMap(right).getOrDefault(name, 0),
                        safeMap(right).getOrDefault(name, 0) - safeMap(left).getOrDefault(name, 0)
                ))
                .filter(delta -> delta.delta() != 0)
                .sorted(Comparator.comparing(VersionCountDelta::name))
                .toList();
    }

    private List<String> riskHints(
            DrawingVersion leftVersion,
            DrawingVersion rightVersion,
            WorkerSummary left,
            WorkerSummary right,
            int entityCountDelta,
            List<String> addedEmptyLayers,
            List<String> removedLayers,
            List<String> addedTexts,
            List<String> removedBlocks
    ) {
        List<String> risks = new ArrayList<>();
        if (safe(leftVersion.id).equals(safe(rightVersion.id))) {
            risks.add("左右版本相同，当前对比不能证明整改变化。");
        }
        if (!safe(leftVersion.versionNo).isBlank() && safe(leftVersion.versionNo).equals(safe(rightVersion.versionNo))) {
            risks.add("新旧版本号未变化，可能影响审查报告和整改记录追溯。");
        }
        if (!"SUCCESS".equalsIgnoreCase(safe(leftVersion.parseStatus)) || !"SUCCESS".equalsIgnoreCase(safe(rightVersion.parseStatus))) {
            risks.add("至少一个版本尚未成功解析，差异结果只能作为初步参考。");
        }
        if (entityCountDelta < 0) {
            risks.add("新版本实体数量减少，需确认是否误删结构线、标注或标题栏信息。");
        }
        if (!removedLayers.isEmpty()) {
            risks.add("存在被删除图层，需确认删除是否符合整改预期。");
        }
        if (!addedEmptyLayers.isEmpty()) {
            risks.add("新版本出现空图层，可能表示图纸清理不完整。");
        }
        if (removedBlocks.contains("TITLE_BLOCK")) {
            risks.add("新版本缺少旧版本中的 TITLE_BLOCK，需重点复核标题栏。");
        } else if (safeList(left.blocks()).contains("TITLE_BLOCK") && !safeList(right.blocks()).contains("TITLE_BLOCK")) {
            risks.add("新版本未解析到标题栏块，需确认标题栏是否被改名、删除或解析失败。");
        }
        if (addedTexts.stream().anyMatch(this::containsPlaceholder)) {
            risks.add("新版本新增文本中包含 TBD/TODO/待定等占位内容。");
        }
        if (risks.isEmpty()) {
            risks.add("结构化摘要未发现明显风险，仍需结合正式预览和审图专家复核。");
        }
        return risks;
    }

    private List<String> reviewFocus(
            List<String> addedLayers,
            List<String> removedLayers,
            List<String> addedBlocks,
            List<String> removedBlocks,
            List<String> addedTexts,
            List<String> removedTexts,
            int entityCountDelta
    ) {
        List<String> focus = new ArrayList<>();
        if (entityCountDelta != 0) {
            focus.add("核对实体数量变化是否与本轮整改范围一致。");
        }
        if (!addedLayers.isEmpty() || !removedLayers.isEmpty()) {
            focus.add("复核新增/删除图层是否符合企业图层命名和交付约定。");
        }
        if (!addedBlocks.isEmpty() || !removedBlocks.isEmpty()) {
            focus.add("复核标题栏、图框和关键块参照是否保持完整。");
        }
        if (!addedTexts.isEmpty() || !removedTexts.isEmpty()) {
            focus.add("核对新增/删除文字，确认图号、版次、尺寸说明和占位内容。");
        }
        if (focus.isEmpty()) {
            focus.add("结构化摘要基本一致，建议重点查看图纸预览中的局部几何变化。");
        }
        return focus;
    }

    private String summary(
            DrawingVersion leftVersion,
            DrawingVersion rightVersion,
            int entityCountDelta,
            List<String> addedLayers,
            List<String> removedLayers,
            List<String> addedTexts,
            List<String> removedTexts,
            List<String> riskHints
    ) {
        return String.format(
                Locale.ROOT,
                "从 %s 到 %s：实体数变化 %+d，新增图层 %d，删除图层 %d，文本变化 %d，风险提示 %d。",
                safe(leftVersion.versionNo).isBlank() ? leftVersion.id : leftVersion.versionNo,
                safe(rightVersion.versionNo).isBlank() ? rightVersion.id : rightVersion.versionNo,
                entityCountDelta,
                addedLayers.size(),
                removedLayers.size(),
                addedTexts.size() + removedTexts.size(),
                riskHints.size()
        );
    }

    private boolean containsPlaceholder(String value) {
        String normalized = value == null ? "" : value.toLowerCase(Locale.ROOT);
        return normalized.contains("tbd")
                || normalized.contains("todo")
                || normalized.contains("xxx")
                || normalized.contains("待定")
                || normalized.contains("未定");
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private List<String> safeList(List<String> value) {
        return value == null ? List.of() : value;
    }

    private Map<String, Integer> safeMap(Map<String, Integer> value) {
        return value == null ? Map.of() : value;
    }
}
