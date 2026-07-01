package com.sora.aitravel.workflow.generate;

import static com.sora.aitravel.workflow.generate.state.TripGraphStateKeys.DAY_CONTEXTS;
import static com.sora.aitravel.workflow.generate.state.TripGraphStateKeys.DAY_QUERY_PLANS;
import static com.sora.aitravel.workflow.generate.state.TripGraphStateKeys.DAY_VALIDATION_REPORTS;
import static com.sora.aitravel.workflow.generate.state.TripGraphStateKeys.LOCKED_DAILY_PLANS;
import static com.sora.aitravel.workflow.generate.state.TripGraphStateKeys.RANKED_DAY_DATA_PACKAGES;
import static com.sora.aitravel.workflow.generate.state.TripGraphStateKeys.REQUIREMENT;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.sora.aitravel.dto.model.TravelRequirementDTO;
import com.sora.aitravel.workflow.generate.state.TripGraphStateCodec;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** 初始化逐日生成状态。 */
@Slf4j
@Component
public class DayStateInitNode {


    public Map<String, Object> execute(OverAllState state) {
        TravelRequirementDTO requirement = TripGraphStateCodec.required(state, REQUIREMENT, TravelRequirementDTO.class);
        log.info("节点[day-state-init]：初始化逐日生成状态，days={}", requirement.getDays());
        return TripGraphStateCodec.patch(
                DAY_CONTEXTS, List.of(),
                DAY_QUERY_PLANS, List.of(),
                RANKED_DAY_DATA_PACKAGES, List.of(),
                DAY_VALIDATION_REPORTS, List.of(),
                LOCKED_DAILY_PLANS, new ArrayList<>());
    }
}
