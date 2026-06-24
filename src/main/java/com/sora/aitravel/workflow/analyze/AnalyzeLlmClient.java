package com.sora.aitravel.workflow.analyze;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sora.aitravel.common.enums.ErrorCode;
import com.sora.aitravel.common.exception.BusinessException;
import com.sora.aitravel.dto.model.DestinationSuggestionDTO;
import com.sora.aitravel.dto.model.TravelRequirementDTO;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;

/** Analyze 工作流里的真实 LLM 调用封装。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AnalyzeLlmClient {

    private static final Set<String> PACES = Set.of("LIGHT", "NORMAL", "TIGHT");

    private final ChatModel chatModel;
    private final ObjectMapper objectMapper;

    public TravelRequirementDTO extractRequirement(String cleanInput, String selectedDestination) {
        String json =
                callJsonObject(
                        """
                        你是旅行规划系统的 Analyze 节点，只做“需求抽取”，不要生成行程。
                        请从用户输入中抽取旅行需求，并只返回 JSON 对象，不要 Markdown。

                        重要规则：
                        1. 没有明确提到的字段返回 null 或空数组，不要编造。
                        2. 如果“用户已选择目的地”存在，destination 必须使用该值。
                        3. days 只提取天数；budget 只提取人民币金额；peopleCount 只提取人数。
                        4. pace 只能是 LIGHT、NORMAL、TIGHT 之一；不明确时返回 null。
                        5. budgetType 只能是 TOTAL、DAILY、PER_PERSON 之一；不明确时返回 null。

                        JSON 字段：
                        {
                          "departure": string|null,
                          "destination": string|null,
                          "days": number|null,
                          "budget": number|null,
                          "budgetType": "TOTAL"|"DAILY"|"PER_PERSON"|null,
                          "peopleCount": number|null,
                          "preferences": string[],
                          "pace": string|null,
                          "avoidances": string[],
                          "travelDate": string|null
                        }

                        用户输入：
                        %s
                        """
                                .formatted(cleanInput));
        JsonNode root = readTree(json);

        String destination = firstNonBlank(selectedDestination, text(root, "destination"));

        return new TravelRequirementDTO(
                text(root, "departure"),
                destination,
                null,
                null,
                null,
                List.of(),
                null,
                "NO_RENTAL",
                null,
                integer(root, "days"),
                integer(root, "budget"),
                text(root, "budgetType"),
                integer(root, "peopleCount"),
                stringList(root.get("preferences")),
                enumValue(root, "pace", PACES, null),
                stringList(root.get("avoidances")),
                text(root, "travelDate"));
    }

    public List<DestinationSuggestionDTO> recommendDestinations(
            String cleanInput, TravelRequirementDTO requirement) {
        String json =
                callJsonArray(
                        """
                        你是旅行规划系统的目的地推荐节点。
                        请基于用户输入和已抽取需求，推荐 3 个中国境内旅行目的地。
                        只返回 JSON 数组，不要 Markdown，不要解释。

                        每个元素格式：
                        {
                          "name": string,
                          "reason": string,
                          "tags": string[],
                          "recommendedDays": number
                        }

                        用户输入：
                        %s

                        已抽取需求：
                        %s
                        """
                                .formatted(cleanInput, toJson(requirement)));
        JsonNode root = readTree(json);
        List<DestinationSuggestionDTO> result = new ArrayList<>();
        if (!root.isArray()) {
            throw new BusinessException(ErrorCode.AI_RESPONSE_FORMAT_ERROR, "目的地推荐结果不是 JSON 数组");
        }
        for (JsonNode item : root) {
            String name = text(item, "name");
            if (!hasText(name)) {
                continue;
            }
            result.add(
                    new DestinationSuggestionDTO(
                            name,
                            firstNonBlank(text(item, "reason"), "符合当前旅行偏好。"),
                            stringList(item.get("tags")),
                            integer(item, "recommendedDays")));
        }
        return result;
    }

    private String callJsonObject(String prompt) {
        return extractJson(call(prompt), '{', '}');
    }

    private String callJsonArray(String prompt) {
        return extractJson(call(prompt), '[', ']');
    }

    private String call(String prompt) {
        try {
            return chatModel.call(new Prompt(prompt)).getResult().getOutput().getText();
        } catch (Exception ex) {
            log.warn("Analyze LLM 调用失败", ex);
            throw new BusinessException(ErrorCode.AI_SERVICE_ERROR, "AI 分析服务调用失败");
        }
    }

    private String extractJson(String content, char startChar, char endChar) {
        if (!hasText(content)) {
            throw new BusinessException(ErrorCode.AI_RESPONSE_FORMAT_ERROR, "AI 返回为空");
        }
        int start = content.indexOf(startChar);
        int end = content.lastIndexOf(endChar);
        if (start < 0 || end <= start) {
            throw new BusinessException(ErrorCode.AI_RESPONSE_FORMAT_ERROR, "AI 未返回合法 JSON");
        }
        return content.substring(start, end + 1);
    }

    private JsonNode readTree(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.AI_RESPONSE_FORMAT_ERROR, "AI 返回 JSON 解析失败");
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.AI_RESPONSE_FORMAT_ERROR, "需求上下文序列化失败");
        }
    }

    private String enumValue(
            JsonNode root, String field, Set<String> allowed, String defaultValue) {
        String value = text(root, field);
        return allowed.contains(value) ? value : defaultValue;
    }

    private Integer integer(JsonNode root, String field) {
        JsonNode node = root == null ? null : root.get(field);
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isNumber()) {
            return node.asInt();
        }
        String value = node.asText();
        if (!hasText(value)) {
            return null;
        }
        try {
            String normalized = value.toLowerCase();
            if (normalized.matches("\\d+(\\.\\d+)?\\s*k")) {
                return (int) (Double.parseDouble(normalized.replace("k", "").trim()) * 1000);
            }
            Integer chineseNumber = chineseNumber(normalized);
            if (chineseNumber != null) {
                return chineseNumber;
            }
            return Integer.parseInt(normalized.replaceAll("[^0-9-]", ""));
        } catch (Exception ex) {
            return null;
        }
    }

    private Integer chineseNumber(String value) {
        String text = value.replaceAll("[天日元人个,，\\s]", "");
        if (text.isBlank()) {
            return null;
        }
        return switch (text) {
            case "一" -> 1;
            case "二", "两" -> 2;
            case "三" -> 3;
            case "四" -> 4;
            case "五" -> 5;
            case "六" -> 6;
            case "七" -> 7;
            case "八" -> 8;
            case "九" -> 9;
            case "十" -> 10;
            default -> null;
        };
    }

    private String text(JsonNode root, String field) {
        JsonNode node = root == null ? null : root.get(field);
        if (node == null || node.isNull()) {
            return null;
        }
        String value = node.asText();
        return hasText(value) && !"null".equalsIgnoreCase(value) ? value.trim() : null;
    }

    private List<String> stringList(JsonNode node) {
        if (node == null || node.isNull()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        if (node.isArray()) {
            for (JsonNode item : node) {
                String value = item.asText();
                if (hasText(value)) {
                    result.add(value.trim());
                }
            }
            return result;
        }
        String value = node.asText();
        if (hasText(value)) {
            for (String item : value.split("[,，、]")) {
                if (hasText(item)) {
                    result.add(item.trim());
                }
            }
        }
        return result;
    }

    private String firstNonBlank(String first, String second) {
        return hasText(first) ? first.trim() : hasText(second) ? second.trim() : null;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
