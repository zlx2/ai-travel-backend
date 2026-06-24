package com.sora.aitravel.workflow.analyze;

import com.sora.aitravel.common.enums.ErrorCode;
import com.sora.aitravel.common.exception.BusinessException;
import com.sora.aitravel.dto.model.TravelRequirementDTO;
import com.sora.aitravel.dto.request.TripAnalyzeRequest;
import java.util.StringJoiner;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** 合并用户文本、表单、追问回答和已选择目的地。 */
@Slf4j
@Component
public class InputPreprocessNode {

    public void execute(AnalyzeWorkflowContext context) {
        TripAnalyzeRequest request = context.getRequest();
        if (request == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "分析请求不能为空");
        }

        StringJoiner input = new StringJoiner("；");
        append(input, request.userInput());
        appendRequirement(input, request.formInput());
        appendRequirement(input, request.requirement());
        if (request.extraAnswers() != null && !request.extraAnswers().isEmpty()) {
            append(input, "补充回答：" + String.join("；", request.extraAnswers()));
        }
        append(input, prefix("用户已选择目的地", request.selectedDestination()));

        String cleanInput = input.toString().replaceAll("\\s+", " ").trim();
        if (cleanInput.isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "请先输入旅行需求");
        }

        context.setCleanInput(cleanInput);
        log.info("节点[input-preprocess]：已合并用户文本、表单、追问回答和已选目的地。");
    }

    private void appendRequirement(StringJoiner input, TravelRequirementDTO requirement) {
        if (requirement == null) {
            return;
        }
        append(input, prefix("表单出发地", requirement.departure()));
        append(input, prefix("表单目的地", requirement.destination()));
        append(input, prefix("表单路线区域", requirement.routeRegion()));
        if (requirement.routeCities() != null && !requirement.routeCities().isEmpty()) {
            append(input, "表单途经城市：" + String.join("、", requirement.routeCities()));
        }
        append(input, requirement.days() == null ? null : "表单天数：" + requirement.days());
        append(input, requirement.budget() == null ? null : "表单预算：" + requirement.budget());
        append(
                input,
                requirement.peopleCount() == null ? null : "表单人数：" + requirement.peopleCount());
        if (requirement.preferences() != null && !requirement.preferences().isEmpty()) {
            append(input, "表单偏好：" + String.join("、", requirement.preferences()));
        }
        append(input, prefix("表单节奏", requirement.pace()));
        append(input, prefix("表单出行日期", requirement.travelDate()));
    }

    private void append(StringJoiner input, String value) {
        if (value != null && !value.isBlank()) {
            input.add(value.trim());
        }
    }

    private String prefix(String label, String value) {
        return value == null || value.isBlank() ? null : label + "：" + value.trim();
    }
}
