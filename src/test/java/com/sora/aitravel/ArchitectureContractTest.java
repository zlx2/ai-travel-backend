package com.sora.aitravel;

import static org.assertj.core.api.Assertions.assertThat;

import com.sora.aitravel.common.enums.AnalyzeStatusEnum;
import com.sora.aitravel.dto.model.TravelRequirementDTO;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class ArchitectureContractTest {
    @Test
    void analyzeStatusMustContainOnlyFourBusinessStates() {
        assertThat(Arrays.stream(AnalyzeStatusEnum.values()).map(Enum::name).toList())
                .containsExactly("READY", "NEED_MORE_INFO", "CONFLICT", "NEED_DESTINATION_CHOICE")
                .doesNotContain("FAIL");
    }

    @Test
    void travelRequirementKeepsGenerateRequiredFields() {
        TravelRequirementDTO requirement =
                new TravelRequirementDTO(
                        "上海",
                        "重庆",
                        "DESTINATION_CITY_TRIP",
                        "SINGLE_CITY",
                        null,
                        List.of("重庆"),
                        "PUBLIC_TRANSIT",
                        "NO_RENTAL",
                        null,
                        3,
                        null,
                        "TOTAL",
                        1,
                        List.of(),
                        "NORMAL",
                        List.of(),
                        null);
        assertThat(requirement.getDeparture()).isEqualTo("上海");
        assertThat(requirement.getDestination()).isEqualTo("重庆");
        assertThat(requirement.getDays()).isBetween(1, 7);
    }
}
