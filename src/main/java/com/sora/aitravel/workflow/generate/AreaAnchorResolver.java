package com.sora.aitravel.workflow.generate;

import com.sora.aitravel.common.enums.ErrorCode;
import com.sora.aitravel.common.exception.BusinessException;
import java.util.List;
import java.util.Map;

/** Resolves AI-returned area references back to canonical anchor ids. */
final class AreaAnchorResolver {

    private AreaAnchorResolver() {}

    static AreaAnchorCandidate resolve(
            Map<String, AreaAnchorCandidate> anchors, String rawId, String field) {
        if (rawId == null || rawId.isBlank()) {
            throw new BusinessException(ErrorCode.AI_GENERATE_ERROR, "路线骨架缺少区域引用：" + field);
        }
        AreaAnchorCandidate exact = anchors.get(rawId);
        if (usable(exact)) {
            return exact;
        }
        List<AreaAnchorCandidate> matches =
                anchors.values().stream()
                        .filter(AreaAnchorResolver::usable)
                        .filter(anchor -> matchesAlias(anchor, rawId))
                        .toList();
        if (matches.size() == 1) {
            return matches.get(0);
        }
        if (matches.size() > 1) {
            throw new BusinessException(
                    ErrorCode.AI_GENERATE_ERROR, "路线骨架区域引用不唯一：" + field + "=" + rawId);
        }
        throw new BusinessException(
                ErrorCode.AI_GENERATE_ERROR, "路线骨架引用无效区域：" + field + "=" + rawId);
    }

    static String canonicalId(
            Map<String, AreaAnchorCandidate> anchors, String rawId, String field) {
        return resolve(anchors, rawId, field).getId();
    }

    static void canonicalizePlan(MacroRoutePlan plan, Map<String, AreaAnchorCandidate> anchors) {
        if (plan == null || plan.getDays() == null) {
            return;
        }
        for (MacroRouteDay day : plan.getDays()) {
            day.setStartAreaId(canonicalId(anchors, day.getStartAreaId(), "startAreaId"));
            day.setEndAreaId(canonicalId(anchors, day.getEndAreaId(), "endAreaId"));
            day.setStayAreaId(canonicalId(anchors, day.getStayAreaId(), "stayAreaId"));
            if (day.getFocusAreaIds() != null) {
                day.setFocusAreaIds(
                        day.getFocusAreaIds().stream()
                                .map(id -> canonicalId(anchors, id, "focusAreaIds"))
                                .toList());
            }
        }
    }

    private static boolean matchesAlias(AreaAnchorCandidate anchor, String rawId) {
        return rawId.equals(anchor.getSourcePoiId())
                || rawId.equals(anchor.getName())
                || anchor.getId().endsWith("_" + rawId);
    }

    private static boolean usable(AreaAnchorCandidate anchor) {
        return anchor != null && anchor.getLocation() != null && !anchor.getLocation().isBlank();
    }
}
