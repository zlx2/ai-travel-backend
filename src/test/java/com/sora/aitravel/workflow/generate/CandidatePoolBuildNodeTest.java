package com.sora.aitravel.workflow.generate;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class CandidatePoolBuildNodeTest {

    @Test
    void shouldBuildScenicAnchorWhenPoiAreaIsMissing() {
        CandidatePoolBuildNode node = new CandidatePoolBuildNode();
        GenerateWorkflowContext context = new GenerateWorkflowContext();
        context.setCityProfile(new CityProfile(
                "成都和都江堰",
                List.of(),
                List.of(),
                List.of(new PoiCandidate(
                        "SCENIC",
                        "都江堰景区",
                        "成都市都江堰市公园路",
                        null,
                        "成都市",
                        "103.616,31.001",
                        "AMAP",
                        "B0TEST",
                        null,
                        null,
                        "110000",
                        null,
                        null,
                        "4.8",
                        null,
                        null,
                        List.of("自然风光"),
                        null,
                        List.of())),
                List.of(),
                List.of()));

        node.execute(context);

        assertThat(context.getCandidatePool().getAreaAnchors())
                .anySatisfy(anchor -> {
                    assertThat(anchor.getRole()).isEqualTo("SCENIC_CLUSTER");
                    assertThat(anchor.getName()).isEqualTo("都江堰景区");
                    assertThat(anchor.getLocation()).isEqualTo("103.616,31.001");
                });
    }
}
