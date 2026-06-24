package com.sora.aitravel.workflow.analyze;

import com.sora.aitravel.common.enums.AnalyzeStatusEnum;
import com.sora.aitravel.dto.model.QuestionDTO;
import com.sora.aitravel.dto.model.TravelRequirementDTO;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** 判断抽取结果是否足够进入下一步，并决定 Graph 的下一条边。 */
@Slf4j
@Component
public class CompletenessCheckNode {

    public void execute(AnalyzeWorkflowContext context) {
        TravelRequirementDTO requirement = context.getExtractedRequirement();
        List<QuestionDTO> questions = new ArrayList<>();

        if (requirement == null) {
            questions.add(new QuestionDTO("userInput", "我还没有理解你的旅行需求，可以再描述一下出发地、想玩几天和偏好吗？", true));
            context.setQuestions(questions);
            context.setStatus(AnalyzeStatusEnum.NEED_MORE_INFO.name());
            return;
        }

        if (isBlank(requirement.departure())) {
            questions.add(new QuestionDTO("departure", "你准备从哪个城市出发？", true));
        }
        if (requirement.days() == null) {
            questions.add(new QuestionDTO("days", "这次旅行大概安排几天？", true));
        }

        if (!questions.isEmpty()) {
            context.setQuestions(questions);
            context.setStatus(AnalyzeStatusEnum.NEED_MORE_INFO.name());
            log.info("节点[completeness-check]：缺少关键信息，进入追问分支，missing={}", questions);
            return;
        }

        if (isBlank(requirement.destination())) {
            boolean hasPreference =
                    requirement.preferences() != null && !requirement.preferences().isEmpty();
            if (hasPreference) {
                context.setStatus(AnalyzeStatusEnum.NEED_DESTINATION_CHOICE.name());
                log.info("节点[completeness-check]：缺少目的地但已有偏好，进入目的地推荐分支。");
            } else {
                context.setQuestions(
                        List.of(new QuestionDTO("destination", "这次有没有明确想去的城市或区域？", true)));
                context.setStatus(AnalyzeStatusEnum.NEED_MORE_INFO.name());
                log.info("节点[completeness-check]：缺少目的地和偏好，进入追问分支。");
            }
            return;
        }

        context.setQuestions(List.of());
        context.setStatus(AnalyzeStatusEnum.READY.name());
        log.info("节点[completeness-check]：关键信息齐全，进入冲突检查分支。");
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
