package com.sora.aitravel.workflow.generate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sora.aitravel.model.trip.generate.AreaAnchorSnapshot;
import com.sora.aitravel.model.trip.generate.CityProfile;
import com.sora.aitravel.model.trip.generate.DayContext;
import com.sora.aitravel.model.trip.generate.DayDataPackage;
import com.sora.aitravel.model.trip.generate.DaySkeleton;
import com.sora.aitravel.model.trip.generate.PoiCandidate;
import com.sora.aitravel.service.PoiIdentityService;
import com.sora.aitravel.service.route.DayRouteOrderService;
import com.sora.aitravel.service.route.RouteOrderOptimizer;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DayPlanGenerateNodeTest {

    @Test
    @DisplayName("轻松节奏多景点日不把单个景区拉到 3.5 小时")
    void shouldCapScenicDurationWhenDayHasMultipleSpots() throws Exception {
        DayPlanGenerateNode node = new DayPlanGenerateNode(null, null, null, null, null, null);

        int minutes =
                (int)
                        invoke(
                                node,
                                "durationMinutes",
                                new Class[] {PoiCandidate.class, String.class, int.class},
                                poi("杭州西湖风景名胜区-五云山"),
                                "LIGHT",
                                3);

        assertEquals(150, minutes);
    }

    @Test
    @DisplayName("轻松节奏两景点日仍保留更长游玩时间")
    void shouldKeepRelaxedDurationWhenDayHasFewSpots() throws Exception {
        DayPlanGenerateNode node = new DayPlanGenerateNode(null, null, null, null, null, null);

        int minutes =
                (int)
                        invoke(
                                node,
                                "durationMinutes",
                                new Class[] {PoiCandidate.class, String.class, int.class},
                                poi("千岛湖风景区"),
                                "LIGHT",
                                2);

        assertEquals(210, minutes);
    }

    @Test
    @DisplayName("长距离自驾日会补充顺路景点，避免一天只开车去一个远端景区")
    void shouldSupplementEnRouteStopForLongRentalDay() throws Exception {
        DayPlanGenerateNode node =
                new DayPlanGenerateNode(null, new PoiIdentityService(), null, null, null, null);
        DayContext dayContext = new DayContext();
        dayContext.setDay(1);
        dayContext.setRentalEnabled(true);
        DaySkeleton skeleton = new DaySkeleton();
        skeleton.setIntensity("LIGHT");
        skeleton.setStartArea(
                new AreaAnchorSnapshot(
                        "start", "杭州东站", "PICKUP", "杭州", "杭州东站", "杭州东站", "120.2100,30.2900"));
        dayContext.setSkeleton(skeleton);
        PoiCandidate qiandao = poi("千岛湖风景区", "119.0500,29.6100", "千岛湖");
        PoiCandidate liangzhu = poi("良渚古城遗址公园", "119.9900,30.3900", "余杭");
        PoiCandidate detour = poi("绍兴鲁迅故里", "120.5800,30.0000", "绍兴");
        CityProfile profile = new CityProfile();
        profile.setScenicCandidates(List.of(qiandao, liangzhu, detour));
        DayDataPackage dataPackage =
                new DayDataPackage(1, List.of(qiandao), List.of(), List.of(), List.of());
        Object input = dayPlanInput(profile);

        @SuppressWarnings("unchecked")
        List<PoiCandidate> result =
                (List<PoiCandidate>)
                        invoke(
                                node,
                                "supplementEnRouteStopForLongRentalDay",
                                new Class[] {
                                    input.getClass(),
                                    DayDataPackage.class,
                                    List.class,
                                    DayContext.class,
                                    java.util.Set.class,
                                    int.class
                                },
                                input,
                                dataPackage,
                                List.of(qiandao),
                                dayContext,
                                new HashSet<>(),
                                3);

        assertEquals(2, result.size());
        assertTrue(result.stream().anyMatch(candidate -> "良渚古城遗址公园".equals(candidate.getName())));
    }

    @Test
    @DisplayName("AI 选点后发现超长单段时会插入沿途点修复路线")
    void shouldRepairLongLegAfterAiSelection() throws Exception {
        PoiIdentityService identityService = new PoiIdentityService();
        DayPlanGenerateNode node =
                new DayPlanGenerateNode(
                        null,
                        identityService,
                        new DayRouteOrderService(new RouteOrderOptimizer(), identityService),
                        null,
                        null,
                        null);
        DayContext dayContext = new DayContext();
        dayContext.setDay(1);
        dayContext.setRentalEnabled(true);
        DaySkeleton skeleton = new DaySkeleton();
        skeleton.setIntensity("LIGHT");
        skeleton.setStartArea(
                new AreaAnchorSnapshot(
                        "start", "杭州东站", "PICKUP", "杭州", "杭州东站", "杭州东站", "120.2100,30.2900"));
        skeleton.setStayArea(
                new AreaAnchorSnapshot(
                        "stay", "千岛湖住宿区", "STAY", "杭州", "千岛湖", "千岛湖", "119.0500,29.6100"));
        dayContext.setSkeleton(skeleton);
        PoiCandidate hangzhou = poi("中国京杭大运河博物馆", "120.1500,30.3100", "拱墅");
        PoiCandidate qiandao = poi("千岛湖风景区", "119.0500,29.6100", "千岛湖");
        PoiCandidate fuchun = poi("富春江小三峡", "119.6700,29.7900", "桐庐");
        CityProfile profile = new CityProfile();
        profile.setScenicCandidates(List.of(hangzhou, qiandao, fuchun));
        DayDataPackage dataPackage =
                new DayDataPackage(1, List.of(hangzhou, qiandao), List.of(), List.of(), List.of());
        Object input = dayPlanInput(profile);

        @SuppressWarnings("unchecked")
        List<PoiCandidate> result =
                (List<PoiCandidate>)
                        invoke(
                                node,
                                "repairLongRentalLegs",
                                new Class[] {
                                    input.getClass(),
                                    DayDataPackage.class,
                                    List.class,
                                    DayContext.class,
                                    java.util.Set.class
                                },
                                input,
                                dataPackage,
                                List.of(hangzhou, qiandao),
                                dayContext,
                                new HashSet<>());

        assertEquals(3, result.size());
        assertTrue(result.stream().anyMatch(candidate -> "富春江小三峡".equals(candidate.getName())));
    }

    private Object invoke(Object target, String name, Class<?>[] parameterTypes, Object... args)
            throws Exception {
        Method method = target.getClass().getDeclaredMethod(name, parameterTypes);
        method.setAccessible(true);
        return method.invoke(target, args);
    }

    private Object dayPlanInput(CityProfile profile) throws Exception {
        Class<?> type =
                Class.forName(
                        "com.sora.aitravel.workflow.generate.DayPlanGenerateNode$DayPlanInput");
        Constructor<?> constructor =
                type.getDeclaredConstructor(
                        com.sora.aitravel.dto.model.TravelRequirementDTO.class,
                        CityProfile.class,
                        List.class,
                        List.class,
                        List.class);
        constructor.setAccessible(true);
        return constructor.newInstance(null, profile, List.of(), List.of(), List.of());
    }

    private PoiCandidate poi(String name) {
        PoiCandidate candidate = new PoiCandidate();
        candidate.setName(name);
        return candidate;
    }

    private PoiCandidate poi(String name, String location, String area) {
        PoiCandidate candidate = new PoiCandidate();
        candidate.setName(name);
        candidate.setLocation(location);
        candidate.setArea(area);
        candidate.setCity("杭州");
        candidate.setSource("AMAP");
        candidate.setSourcePoiId(name);
        candidate.setRating("4.7");
        return candidate;
    }
}
