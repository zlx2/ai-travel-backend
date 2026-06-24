package com.sora.aitravel.workflow.generate;

import com.sora.aitravel.dto.model.TravelRequirementDTO;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** 查询城市基础数据池：热门景点、美食区域、住宿区域和交通枢纽。 */
@Slf4j
@Component
public class CityDataProfileNode {

    public void execute(GenerateWorkflowContext context) {
        TravelRequirementDTO requirement = context.getRequirement();
        String destination = displayDestination(requirement);

        // TODO 接入 AmapPoiClient：查询景区、公园、博物馆、商圈、酒店、餐饮、地铁站、火车站、机场。
        log.info("节点[city-data-profile]：TODO 调用工具查询 {} 城市基础 POI 数据，当前使用模拟数据。", destination);

        List<String> popularAreas =
                List.of(destination + "核心商圈", destination + "老城区域", destination + "休闲街区");
        CityProfile profile =
                new CityProfile(
                        destination,
                        popularAreas,
                        List.of(destination + "火车站", destination + "机场", destination + "中心地铁站"),
                        List.of(
                                scenic(destination, "城市地标", "核心商圈", "适合初到目的地建立城市印象。"),
                                scenic(destination, "历史文化街区", "老城区域", "适合慢节奏步行和文化体验。"),
                                scenic(destination, "自然景区", "自然景区周边", "适合自然风光偏好。")),
                        List.of(
                                food(destination, "本地小吃街", "老城区域", "选择多，适合午餐或下午茶。"),
                                food(destination, "特色餐饮街区", "核心商圈", "适合晚餐，交通方便。"),
                                food(destination, "夜市美食区", "休闲街区", "适合晚间自由活动。")),
                        List.of(
                                hotel(destination, "核心商圈住宿区域", "核心商圈", "交通和餐饮便利。"),
                                hotel(destination, "交通枢纽住宿区域", "交通枢纽", "适合早到晚走。"),
                                hotel(destination, "老城慢游住宿区域", "老城区域", "适合轻松游。")));

        context.setCityProfile(profile);
    }

    private PoiCandidate scenic(String destination, String name, String area, String reason) {
        return candidate("SCENIC", destination + name, area, reason);
    }

    private PoiCandidate food(String destination, String name, String area, String reason) {
        return candidate("FOOD", destination + name, area, reason);
    }

    private PoiCandidate hotel(String destination, String name, String area, String reason) {
        return candidate("HOTEL", destination + name, area, reason);
    }

    private PoiCandidate candidate(String category, String name, String area, String reason) {
        return new PoiCandidate(
                category,
                name,
                area + "模拟地址",
                area,
                "104.000000,30.000000",
                "SIMULATED_AMAP",
                "TODO_" + Math.abs(name.hashCode()),
                reason,
                null);
    }

    private String displayDestination(TravelRequirementDTO requirement) {
        if (requirement.getDestination() != null && !requirement.getDestination().isBlank()) {
            return requirement.getDestination();
        }
        if (requirement.getRouteRegion() != null && !requirement.getRouteRegion().isBlank()) {
            return requirement.getRouteRegion();
        }
        return String.join("-", requirement.getRouteCities());
    }
}
