package com.sora.aitravel.workflow.analyze;

import com.sora.aitravel.common.enums.ErrorCode;
import com.sora.aitravel.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** 接收用户文字、表单、追问回答和已选择目的地。 */
@Slf4j
@Component
public class UserRawInputNode {

    public void execute(AnalyzeWorkflowContext context) {
        if (context.getRequest() == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "分析请求不能为空");
        }
        log.info("节点[user-raw-input]：已接收 Analyze 原始输入。");
    }
}
