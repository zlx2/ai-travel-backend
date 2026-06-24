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
        append(input, request.getUserInput());
        appendRequirement(input, request.getFormInput());
        appendRequirement(input, request.getRequirement());
        if (request.getExtraAnswers() != null && !request.getExtraAnswers().isEmpty()) {
            append(input, "补充回答：" + String.join("；", request.getExtraAnswers()));
        }
        append(input, prefix("用户已选择目的地", request.getSelectedDestination()));

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
        append(input, prefix("表单出发地", requirement.getDeparture()));
        append(input, prefix("表单目的地", requirement.getDestination()));
        append(input, prefix("表单路线模式", requirement.getRouteMode()));
        append(input, prefix("表单路线结构", requirement.getRouteStructure()));
        append(input, prefix("表单路线区域", requirement.getRouteRegion()));
        if (requirement.getRouteCities() != null && !requirement.getRouteCities().isEmpty()) {
            append(input, "表单途经城市：" + String.join("、", requirement.getRouteCities()));
        }
        append(input, prefix("表单交通方式", requirement.getTransportMode()));
        append(input, prefix("表单租车意图", requirement.getRentalIntent()));
        if (requirement.getRentalRequirement() != null) {
            append(
                    input,
                    requirement.getRentalRequirement().getNeedRental() == null
                            ? null
                            : "表单是否租车：" + requirement.getRentalRequirement().getNeedRental());
            append(input, prefix("表单取车城市", requirement.getRentalRequirement().getPickupCity()));
            append(input, prefix("表单还车城市", requirement.getRentalRequirement().getReturnCity()));
            append(
                    input,
                    prefix("表单车型偏好", requirement.getRentalRequirement().getVehiclePreference()));
        }
        append(input, requirement.getDays() == null ? null : "表单天数：" + requirement.getDays());
        append(input, requirement.getBudget() == null ? null : "表单预算：" + requirement.getBudget());
        append(
                input,
                requirement.getPeopleCount() == null
                        ? null
                        : "表单人数：" + requirement.getPeopleCount());
        if (requirement.getPreferences() != null && !requirement.getPreferences().isEmpty()) {
            append(input, "表单偏好：" + String.join("、", requirement.getPreferences()));
        }
        append(input, prefix("表单节奏", requirement.getPace()));
        append(input, prefix("表单出行日期", requirement.getTravelDate()));
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
