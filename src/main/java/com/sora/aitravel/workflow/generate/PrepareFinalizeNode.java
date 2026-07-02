package com.sora.aitravel.workflow.generate;

import static com.sora.aitravel.workflow.generate.TripGraphStateKeys.DAY_CONTEXTS;
import static com.sora.aitravel.workflow.generate.TripGraphStateKeys.DAY_QUERY_PLANS;
import static com.sora.aitravel.workflow.generate.TripGraphStateKeys.DAY_SKELETONS;
import static com.sora.aitravel.workflow.generate.TripGraphStateKeys.DAY_VALIDATION_REPORTS;
import static com.sora.aitravel.workflow.generate.TripGraphStateKeys.LOCKED_DAILY_PLANS;
import static com.sora.aitravel.workflow.generate.TripGraphStateKeys.RANKED_DAY_DATA_PACKAGES;
import static com.sora.aitravel.workflow.generate.TripGraphStateKeys.REQUIREMENT;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.sora.aitravel.common.enums.ErrorCode;
import com.sora.aitravel.common.exception.BusinessException;
import com.sora.aitravel.dto.model.TravelRequirementDTO;
import com.sora.aitravel.model.DaySkeleton;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** Validates prepared state and initializes per-day generation state. */
@Slf4j
@Component
public class PrepareFinalizeNode {

    public Map<String, Object> execute(OverAllState state) {
        validatePreparedState(state);
        TravelRequirementDTO requirement =
                TripGraphStateCodec.required(state, REQUIREMENT, TravelRequirementDTO.class);
        log.info("节点[prepare-finalize]：初始化逐日生成状态，days={}", requirement.getDays());
        return TripGraphStateCodec.patch(
                DAY_CONTEXTS, List.of(),
                DAY_QUERY_PLANS, List.of(),
                RANKED_DAY_DATA_PACKAGES, List.of(),
                DAY_VALIDATION_REPORTS, List.of(),
                LOCKED_DAILY_PLANS, new ArrayList<>());
    }

    private void validatePreparedState(OverAllState state) {
        TravelRequirementDTO requirement =
                TripGraphStateCodec.required(state, REQUIREMENT, TravelRequirementDTO.class);
        List<DaySkeleton> daySkeletons =
                TripGraphStateCodec.optionalList(state, DAY_SKELETONS, DaySkeleton.class);
        int days = requirement.getDays();
        if (daySkeletons == null || daySkeletons.size() != days) {
            throw new BusinessException(ErrorCode.AI_GENERATE_ERROR, "行程骨架数量与天数不一致");
        }
    }
}
