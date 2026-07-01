package com.sora.aitravel.workflow.generate;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.sora.aitravel.dto.model.TravelRequirementDTO;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AiMacroRoutePlanNodeTest {

    @Test
    @DisplayName("环线路线先安排近处，再安排远处，最后回到近处区域")
    void shouldOrderLoopRouteByOutAndBackShape() throws Exception {
        AiMacroRoutePlanNode node = new AiMacroRoutePlanNode(null, null);
        CandidatePool pool = new CandidatePool();
        pool.setPickupAnchor(anchor("pickup", "杭州东站", "PICKUP", "120.2100,30.2900"));
        List<AreaAnchorCandidate> scenic = List.of(
                anchor("qiandao", "千岛湖", "SCENIC_CLUSTER", "119.0500,29.6100"),
                anchor("xihu", "西湖", "SCENIC_CLUSTER", "120.1400,30.2500"),
                anchor("jiuxi", "九溪", "SCENIC_CLUSTER", "120.1100,30.1700"));

        @SuppressWarnings("unchecked")
        List<AreaAnchorCandidate> ordered = (List<AreaAnchorCandidate>)
                invoke(node, "orderScenicAnchors", new Class[] {String.class, int.class, List.class, CandidatePool.class},
                        "LOOP", 3, scenic, pool);

        assertEquals("xihu", ordered.get(0).getId());
        assertEquals("qiandao", ordered.get(1).getId());
        assertEquals("jiuxi", ordered.get(2).getId());
    }

    @Test
    @DisplayName("非环线按离起点由近到远推进")
    void shouldOrderOneWayRouteByDistanceFromOrigin() throws Exception {
        AiMacroRoutePlanNode node = new AiMacroRoutePlanNode(null, null);
        CandidatePool pool = new CandidatePool();
        pool.setPickupAnchor(anchor("pickup", "杭州东站", "PICKUP", "120.2100,30.2900"));
        List<AreaAnchorCandidate> scenic = List.of(
                anchor("qiandao", "千岛湖", "SCENIC_CLUSTER", "119.0500,29.6100"),
                anchor("xihu", "西湖", "SCENIC_CLUSTER", "120.1400,30.2500"),
                anchor("jiuxi", "九溪", "SCENIC_CLUSTER", "120.1100,30.1700"));

        @SuppressWarnings("unchecked")
        List<AreaAnchorCandidate> ordered = (List<AreaAnchorCandidate>)
                invoke(node, "orderScenicAnchors", new Class[] {String.class, int.class, List.class, CandidatePool.class},
                        "ONEWAY", 3, scenic, pool);

        assertEquals("xihu", ordered.get(0).getId());
        assertEquals("jiuxi", ordered.get(1).getId());
        assertEquals("qiandao", ordered.get(2).getId());
    }

    @Test
    @DisplayName("用户指定不走回头路时识别为非环线")
    void shouldResolveOneWayRouteShapeFromRequirement() throws Exception {
        AiMacroRoutePlanNode node = new AiMacroRoutePlanNode(null, null);
        TravelRequirementDTO requirement = new TravelRequirementDTO();
        requirement.setRouteStructure("不走回头路，沿一个方向玩");

        String shape = (String) invoke(
                node,
                "routeShape",
                new Class[] {TravelRequirementDTO.class, com.sora.aitravel.dto.model.RentalQuoteOptionDTO.class},
                requirement,
                null);

        assertEquals("ONEWAY", shape);
    }

    private Object invoke(Object target, String name, Class<?>[] parameterTypes, Object... args) throws Exception {
        Method method = target.getClass().getDeclaredMethod(name, parameterTypes);
        method.setAccessible(true);
        return method.invoke(target, args);
    }

    private AreaAnchorCandidate anchor(String id, String name, String role, String location) {
        AreaAnchorCandidate anchor = new AreaAnchorCandidate();
        anchor.setId(id);
        anchor.setName(name);
        anchor.setRole(role);
        anchor.setCity("杭州");
        anchor.setArea(name);
        anchor.setLocation(location);
        return anchor;
    }
}
