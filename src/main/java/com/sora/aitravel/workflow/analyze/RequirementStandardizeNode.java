package com.sora.aitravel.workflow.analyze;

import com.sora.aitravel.dto.model.TravelRequirementDTO;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** 用规则稳定 AI 抽取结果，不调用模型。 */
@Slf4j
@Component
public class RequirementStandardizeNode {

    public void execute(AnalyzeWorkflowContext context) {
        TravelRequirementDTO extracted = context.getExtractedRequirement();
        if (extracted == null) {
            context.setExtractedRequirement(null);
            return;
        }

        String destination =
                firstNonBlank(context.getRequest().selectedDestination(), extracted.destination());
        List<String> routeCities = cleanList(extracted.routeCities());
        if (routeCities.isEmpty() && hasText(destination)) {
            routeCities = List.of(destination);
        }

        TravelRequirementDTO standardized =
                new TravelRequirementDTO(
                        cleanText(extracted.departure()),
                        cleanText(destination),
                        defaultText(extracted.routeMode(), "DESTINATION_CITY_TRIP"),
                        defaultText(extracted.routeStructure(), "SINGLE_CITY"),
                        cleanText(extracted.routeRegion()),
                        routeCities,
                        defaultText(extracted.transportMode(), "PUBLIC_TRANSIT"),
                        "NO_RENTAL",
                        null,
                        extracted.days(),
                        extracted.budget(),
                        normalizeBudgetType(extracted.budgetType()),
                        extracted.peopleCount() == null ? 1 : extracted.peopleCount(),
                        cleanList(extracted.preferences()),
                        normalizePace(extracted.pace()),
                        cleanList(extracted.avoidances()),
                        cleanText(extracted.travelDate()));

        context.setExtractedRequirement(standardized);
        log.info("节点[requirement-standardize]：已用规则标准化 Analyze 抽取结果。");
    }

    private String normalizeBudgetType(String value) {
        String text = cleanText(value);
        if (text == null || "随便".equals(text) || "不限".equals(text)) {
            return "TOTAL";
        }
        String upper = text.toUpperCase(Locale.ROOT);
        return switch (upper) {
            case "TOTAL", "DAILY", "PER_PERSON" -> upper;
            default -> "TOTAL";
        };
    }

    private String normalizePace(String value) {
        String text = cleanText(value);
        if (text == null || "随便".equals(text)) {
            return "NORMAL";
        }
        String upper = text.toUpperCase(Locale.ROOT);
        if ("RELAXED".equals(upper)
                || "EASY".equals(upper)
                || text.contains("轻松")
                || text.contains("不累")) {
            return "LIGHT";
        }
        if ("INTENSIVE".equals(upper) || text.contains("紧凑") || text.contains("多玩")) {
            return "TIGHT";
        }
        return switch (upper) {
            case "LIGHT", "NORMAL", "TIGHT" -> upper;
            default -> "NORMAL";
        };
    }

    private List<String> cleanList(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
                .map(this::cleanText)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    private String defaultText(String value, String defaultValue) {
        String text = cleanText(value);
        return text == null ? defaultValue : text;
    }

    private String firstNonBlank(String first, String second) {
        return hasText(first) ? first.trim() : cleanText(second);
    }

    private String cleanText(String value) {
        if (!hasText(value)) {
            return null;
        }
        String text = value.trim();
        return "null".equalsIgnoreCase(text) || "无".equals(text) || "暂无".equals(text)
                ? null
                : text;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
