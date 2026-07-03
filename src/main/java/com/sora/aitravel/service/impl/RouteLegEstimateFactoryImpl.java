package com.sora.aitravel.service.impl;

import com.sora.aitravel.dto.model.TripPlanDTO;
import com.sora.aitravel.service.route.GeoRouteCalculator;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 行程路线段估算工厂。
 *
 * <p>根据每日有序景点列表，为相邻景点间生成路线段估算结果（距离、耗时、费用、交通方式）。
 * 在行程规划生成阶段使用，不请求外部路线 API，纯靠直线距离 + 经验系数推算，保证速度。
 *
 * <p>交通方式选择逻辑：
 * <ul>
 *   <li>未启用租车且直线距离 ≤ 2 km → 步行</li>
 *   <li>启用租车 → 自驾（行驶速度随距离阶梯变化：35/40/45 km/h）</li>
 *   <li>否则 → 打车（含起步价 + 里程费 + 时长费）</li>
 * </ul>
 */
@Component
public class RouteLegEstimateFactoryImpl {
    /** 步行模式标识。 */
    private static final String MODE_WALK = "WALK";
    /** 打车模式标识。 */
    private static final String MODE_TAXI = "TAXI";
    /** 自驾模式标识。 */
    private static final String MODE_DRIVING = "DRIVING";
    /** 数据来源标识——纯估算，非真实路线 API。 */
    private static final String SOURCE_ESTIMATED = "ESTIMATED";
    /** 步行模式最大直线距离（km），超过此距离不再推荐步行。 */
    private static final double WALKING_ROUTE_MAX_KM = 2.0;
    /** 短途自驾参考时速（km/h），用于 ≤ 5 km。 */
    private static final double DRIVING_SPEED_SHORT_KMH = 35.0;
    /** 中途自驾参考时速（km/h），用于 5~10 km。 */
    private static final double DRIVING_SPEED_MEDIUM_KMH = 40.0;
    /** 长途自驾参考时速（km/h），用于 ≥ 10 km。 */
    private static final double DRIVING_SPEED_LONG_KMH = 45.0;
    /** 短途/中途速度分界阈值（km）。 */
    private static final double DRIVING_SPEED_SHORT_THRESHOLD_KM = 5.0;
    /** 中途/长途速度分界阈值（km）。 */
    private static final double DRIVING_SPEED_LONG_THRESHOLD_KM = 10.0;

    /**
     * 为整日行程中所有相邻景点构建路线段估算列表。
     *
     * <p>若景点数 < 2（无可衔接的段）则返回空列表。每个相邻对会生成一条 RouteLeg，
     * 包含交通方式、距离、耗时、费用和文字描述。
     *
     * @param spots         按 order 排序的景点列表
     * @param rentalEnabled 是否已租车（影响交通方式选择与计价）
     * @return 各段路线估算结果
     */
    public List<TripPlanDTO.RouteLeg> build(List<TripPlanDTO.Spot> spots, boolean rentalEnabled) {
        List<TripPlanDTO.RouteLeg> legs = new ArrayList<>();
        if (spots == null || spots.size() < 2) {
            return legs;
        }
        for (int index = 0; index < spots.size() - 1; index++) {
            TripPlanDTO.Spot from = spots.get(index);
            TripPlanDTO.Spot to = spots.get(index + 1);
            TripPlanDTO.RouteLeg leg = buildLeg(from, to, rentalEnabled);
            legs.add(leg);
        }
        return legs;
    }

    /**
     * 为相邻两景点构建单条路线段。
     *
     * <p>提取两点的坐标进行估算，然后将距离、耗时、费用、模式、文字建议一并写入 RouteLeg。
     * fromOrder / toOrder 反映在行程中的位置顺序。
     */
    private TripPlanDTO.RouteLeg buildLeg(
            TripPlanDTO.Spot from, TripPlanDTO.Spot to, boolean rentalEnabled) {
        double[] fromLocation = spotLocation(from);
        double[] toLocation = spotLocation(to);
        RouteEstimate estimate = estimate(fromLocation, toLocation, rentalEnabled);
        TripPlanDTO.RouteLeg leg = new TripPlanDTO.RouteLeg();
        leg.setFromOrder(from.getOrder());
        leg.setToOrder(to.getOrder());
        leg.setMode(estimate.mode());
        leg.setSuggestion(
                "从" + from.getName() + "前往" + to.getName() + "，" + estimate.description());
        leg.setDistanceMeters(estimate.distanceMeters());
        leg.setDurationMinutes(estimate.durationMinutes());
        leg.setEstimatedCost(estimate.cost());
        leg.setSource(SOURCE_ESTIMATED);
        return leg;
    }

    /**
     * 核心估算逻辑：根据两点坐标及是否租车，计算路线段的各项指标。
     *
     * <p>步骤：
     * <ol>
     *   <li>坐标任一缺失 → 返回 UNKNOWN 占位，由调用方决定如何展示；
     *   <li>计算两点间的大圆直线距离；
     *   <li>按道路曲折系数折算实际路距（步行系数 1.15，其余使用 {@link GeoRouteCalculator} 默认值）；
     *   <li>根据模式选择车速（步行固定速度，自驾按距离阶梯，打车用城市均速）；
     *   <li>估算耗时 = 路距 / 车速（向上取整）；
     *   <li>估算费用：步行 0，自驾按 0.8 元/km，打车按起步价 + 里程 + 时长计价模式。
     * </ol>
     */
    private RouteEstimate estimate(double[] from, double[] to, boolean rentalEnabled) {
        if (from == null || to == null) {
            return new RouteEstimate(
                    Integer.MAX_VALUE / 4,
                    null,
                    null,
                    "UNKNOWN",
                    rentalEnabled ? "坐标不足，建议自驾衔接。" : "坐标不足，建议灵活选择步行或打车。");
        }
        double directKm = GeoRouteCalculator.distanceKm(from[0], from[1], to[0], to[1]);
        boolean walking = !rentalEnabled && directKm <= WALKING_ROUTE_MAX_KM;
        int distanceMeters =
                (int)
                        Math.round(
                                directKm
                                        * (walking
                                                ? 1.15
                                                : GeoRouteCalculator.DEFAULT_ROAD_DISTANCE_FACTOR)
                                        * 1000);
        double speedKmh =
                walking
                        ? GeoRouteCalculator.WALKING_SPEED_KMH
                        : rentalEnabled
                                ? (directKm > DRIVING_SPEED_LONG_THRESHOLD_KM
                                        ? DRIVING_SPEED_LONG_KMH
                                        : directKm > DRIVING_SPEED_SHORT_THRESHOLD_KM
                                                ? DRIVING_SPEED_MEDIUM_KMH
                                                : DRIVING_SPEED_SHORT_KMH)
                                : GeoRouteCalculator.CITY_DRIVING_SPEED_KMH;
        int durationMinutes =
                (int) Math.ceil(GeoRouteCalculator.travelSeconds(distanceMeters, speedKmh) / 60.0);
        Integer cost =
                walking
                        ? 0
                        : rentalEnabled
                                ? estimateDrivingCost(distanceMeters)
                                : estimateTaxiCost(distanceMeters);
        String mode = walking ? MODE_WALK : rentalEnabled ? MODE_DRIVING : MODE_TAXI;
        return new RouteEstimate(
                distanceMeters,
                durationMinutes,
                cost,
                mode,
                description(distanceMeters, durationMinutes, cost, walking, rentalEnabled));
    }

    /**
     * 从景点对象中提取坐标数组 [lng, lat]。
     *
     * <p>优先使用入口坐标（entranceLng/entranceLat），入口坐标为空时回退到景点中心坐标。
     * 两者都为空或景点本身为 null 时返回 null，由调用方处理坐标缺失的降级。
     */
    private double[] spotLocation(TripPlanDTO.Spot spot) {
        if (spot == null) {
            return null;
        }
        java.math.BigDecimal lng =
                spot.getEntranceLng() == null ? spot.getLng() : spot.getEntranceLng();
        java.math.BigDecimal lat =
                spot.getEntranceLat() == null ? spot.getLat() : spot.getEntranceLat();
        if (lng == null || lat == null) {
            return null;
        }
        return new double[] {lng.doubleValue(), lat.doubleValue()};
    }

    /**
     * 生成路线段的文字描述，用于展示给用户。
     *
     * <p>格式固定为"X.X 公里，约 N 分钟"后附加模式说明：
     * <ul>
     *   <li>步行 → 距离较近，可步行或短途打车</li>
     *   <li>打车 → 打车约 ¥cost</li>
     *   <li>自驾 → 自驾能耗/油费约 ¥cost</li>
     * </ul>
     */
    private String description(
            int distanceMeters,
            Integer durationMinutes,
            Integer cost,
            boolean walking,
            boolean rentalEnabled) {
        return formatDistance(distanceMeters)
                + "，约 "
                + durationMinutes
                + " 分钟"
                + (walking ? "，距离较近，可步行或短途打车" : "")
                + (!walking && !rentalEnabled && cost != null ? "，打车约 ¥" + cost : "")
                + (rentalEnabled && cost != null ? "，自驾能耗/油费约 ¥" + cost : "");
    }

    /**
     * 估算自驾能耗/油费。
     *
     * <p>按 0.8 元/km 估算（电动/燃油混估），最少 3 元（覆盖极短途场景）。
     *
     * @param distanceMeters 路距（米），null 时返回 null
     */
    private Integer estimateDrivingCost(Integer distanceMeters) {
        return distanceMeters == null
                ? null
                : Math.max(3, (int) Math.ceil(distanceMeters / 1000.0 * 0.8));
    }

    /**
     * 估算打车费用。
     *
     * <p>计价模型：起步价 13 元（含 3 km），超出部分按 2.5 元/km 叠加，
     * 另按 0.5 元/分钟计入时长费（时长由路距 / 城市均速算出）。
     * 最终结果向上取整。
     *
     * @param distanceMeters 路距（米），null 时返回 null
     */
    private Integer estimateTaxiCost(Integer distanceMeters) {
        if (distanceMeters == null) {
            return null;
        }
        double km = distanceMeters / 1000.0;
        double baseFare = 13;
        int baseKm = 3;
        double perKm = 2.5;
        double perMinute = 0.5;
        int durationMinutes =
                (int)
                        Math.ceil(
                                km
                                        * GeoRouteCalculator.DEFAULT_ROAD_DISTANCE_FACTOR
                                        / GeoRouteCalculator.CITY_DRIVING_SPEED_KMH
                                        * 60);
        double fare = baseFare + Math.max(0, km - baseKm) * perKm + durationMinutes * perMinute;
        return (int) Math.ceil(fare);
    }

    /**
     * 将米数格式化为人类可读的字符串。
     *
     * <p>≥ 1000 m 显示为"X.X 公里"（保留一位小数），不足 1000 m 显示为"N 米"。
     */
    private String formatDistance(int meters) {
        if (meters >= 1000) {
            return String.format("%.1f 公里", meters / 1000.0);
        }
        return meters + " 米";
    }

    /**
     * 路线段估算结果的内聚值对象。
     *
     * <p>在工厂内部承上启下：{@link #estimate} 计算出所有指标后打包为此对象，
     * {@link #buildLeg} 再拆解写入 RouteLeg DTO。五个字段在构造时全部确定，不可变更。
     */
    private static final class RouteEstimate {
        /** 估算路距（米）。 */
        private final int distanceMeters;
        /** 估算耗时（分钟），null 表示无法估算。 */
        private final Integer durationMinutes;
        /** 估算费用（元），null 表示无法估算。 */
        private final Integer cost;
        /** 交通模式：WALK / TAXI / DRIVING / UNKNOWN。 */
        private final String mode;
        /** 面向用户的文字描述。 */
        private final String description;

        private RouteEstimate(
                int distanceMeters,
                Integer durationMinutes,
                Integer cost,
                String mode,
                String description) {
            this.distanceMeters = distanceMeters;
            this.durationMinutes = durationMinutes;
            this.cost = cost;
            this.mode = mode;
            this.description = description;
        }

        private int distanceMeters() {
            return distanceMeters;
        }

        private Integer durationMinutes() {
            return durationMinutes;
        }

        private Integer cost() {
            return cost;
        }

        private String mode() {
            return mode;
        }

        private String description() {
            return description;
        }
    }
}
