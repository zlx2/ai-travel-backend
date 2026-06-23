package com.sora.aitravel.workflow.analyze;

import com.sora.aitravel.dto.model.TravelRequirementDTO;
import com.sora.aitravel.workflow.WorkflowNode;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * 信息提取节点。
 * <p>
 * 实现 {@link WorkflowNode} 接口，是 {@link TripAnalyzeWorkflow} 工作流的第二个步骤。
 * 负责调用 AI 大模型（如 DeepSeek）从用户标准化的自然语言输入中提取关键行程字段，
 * 包括出发地、目的地、出行天数、出行时间、偏好等结构化信息。
 * <p>
 * 在整个工作流中的位置：流程第 2 步（在输入预处理之后，完整性检查之前）。
 * <p>
 * 输入：{@link AnalyzeWorkflowContext#request}（已预处理的用户请求）。
 * 输出：将模型返回的原始 JSON 响应写入 {@link AnalyzeWorkflowContext#rawModelResponse}。
 */
@Component
public class InfoExtractNode implements WorkflowNode<AnalyzeWorkflowContext> {

    private static final List<String> KNOWN_DESTINATIONS =
            List.of("重庆", "成都", "杭州", "西安", "厦门", "云南", "上海", "北京", "三亚", "广州", "深圳");

    private static final List<String> KNOWN_PREFERENCES =
            List.of("美食", "夜景", "历史文化", "自然风光", "亲子", "拍照打卡", "海岛", "轻松游", "自驾", "租车", "周边");

    /**
     * 执行信息提取逻辑——调用 AI 模型从用户输入中提取结构化行程信息。
     *
     * @param context 工作流上下文，读取预处理后的请求并调用模型，
     *                将模型原始响设置到 {@link AnalyzeWorkflowContext#rawModelResponse}
     */
    public void execute(AnalyzeWorkflowContext context) {
        String input = safeText(context.getRequest().userInput());
        String selectedDestination = safeText(context.getRequest().selectedDestination());

        String destination =
                !selectedDestination.isBlank()
                        ? selectedDestination
                        : KNOWN_DESTINATIONS.stream()
                                .filter(input::contains)
                                .findFirst()
                                .orElse("");
        String departure = extractDeparture(input);
        int days = extractInt(input, "(\\d+)\\s*天", 3);
        int budget = extractInt(input, "预算\\s*(\\d+)", 2000);
        List<String> preferences = extractPreferences(input);

        if (preferences.isEmpty()) {
            preferences = List.of("美食", "轻松游");
        }

        context.setExtractedRequirement(
                new TravelRequirementDTO(
                        departure.isBlank() ? "上海" : departure,
                        destination,
                        days,
                        budget,
                        "TOTAL",
                        2,
                        preferences,
                        preferences.contains("轻松游") ? "LIGHT" : "NORMAL",
                        List.of(),
                        null));
        context.setRawModelResponse("MOCK_ANALYZE_EXTRACTED");
    }

    private String extractDeparture(String input) {
        Matcher matcher = Pattern.compile("从([\\u4e00-\\u9fa5]{2,8})去").matcher(input);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "";
    }

    private int extractInt(String input, String regex, int defaultValue) {
        Matcher matcher = Pattern.compile(regex).matcher(input);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        }
        return defaultValue;
    }

    private List<String> extractPreferences(String input) {
        List<String> result = new ArrayList<>();
        for (String preference : KNOWN_PREFERENCES) {
            if (input.contains(preference)) {
                result.add(preference);
            }
        }
        return result;
    }

    private String safeText(String value) {
        return value == null ? "" : value.trim();
    }
}
