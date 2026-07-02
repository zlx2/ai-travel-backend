package com.sora.aitravel.dto.model;

import java.math.BigDecimal;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 前端 AI 行程页使用的完整旅行计划数据传输对象。
 *
 * <p>包含行程标题、每日计划、预算汇总、旅行贴士及数据质量溯源信息。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TripPlanDTO {

    /** 行程标题，如「成都 5 日深度游」 */
    private String title;

    /** 目的地描述，如「成都」「云南」 */
    private String destination;

    /** 行程总天数 */
    private Integer days;

    /** 行程整体摘要/概述文案 */
    private String summary;

    /** AI 推荐的住宿区域建议（整体级别） */
    private AccommodationSuggestion accommodationSuggestion;

    /** 每日行程计划列表 */
    private List<DailyPlan> dailyPlans;

    /** 全程预算汇总 */
    private BudgetSummary budgetSummary;

    /** 全程旅行贴士列表 */
    private List<String> tips;

    /** 数据质量溯源信息，标识各维度数据的来源 */
    private DataQuality dataQuality;

    /**
     * 单日行程计划。
     *
     * <p>包含当天景点、路线、餐饮、时间线及住宿锚点等完整信息。
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DailyPlan {
        /** 第几天（从 1 开始） */
        private Integer day;

        /** 当天主题，如「市区经典打卡」 */
        private String theme;

        /** 行程强度枚举值，如 "RELAXED"、"NORMAL"、"INTENSIVE" */
        private String intensity;

        /** 行程强度中文标签，如「轻松」「适中」「紧凑」 */
        private String intensityLabel;

        /** 当天主要城市 */
        private String city;

        /** 当天推荐就餐区域 */
        private String diningArea;

        /** 当天路线概述文案 */
        private String routeSummary;

        /** 当天游览景点列表（按 order 排序） */
        private List<Spot> spots;

        /** 相邻景点间的路线段列表 */
        private List<RouteLeg> routeLegs;

        /** 当天餐饮推荐列表 */
        private List<FoodSuggestion> foodSuggestions;

        /** 当天专属旅行贴士 */
        private List<String> dayTips;

        /** 当天费用估算（门票 + 餐饮 + 交通） */
        private EstimatedCost estimatedCost;

        /** 当天出发锚点（起点位置） */
        private Anchor startAnchor;

        /** 当天终点锚点（通常为住宿区域），供次日出发计算使用 */
        private Anchor endAnchor;

        /** 当天完整时间线节点列表（含出发、景点、餐饮、住宿等） */
        private List<TimelineNode> timeline;

        /** 当天最后景点附近的酒店列表（高德周边搜索） */
        private List<NearbyHotel> nearbyHotels;
    }

    /**
     * 地理锚点，表示行程中的关键位置（出发点、住宿区域等）。
     *
     * <p>type 取值：DAY_START（出发）、STAY_AREA（住宿区域）、RENTAL_PICKUP（租车取车点）。
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Anchor {
        /** 锚点类型：DAY_START / STAY_AREA / RENTAL_PICKUP */
        private String type;

        /** 锚点显示名称 */
        private String name;

        /** 所在城市 */
        private String city;

        /** 所在区域/商圈 */
        private String area;

        /** 详细地址 */
        private String address;

        /** 经度 */
        private BigDecimal lng;

        /** 纬度 */
        private BigDecimal lat;

        /** 坐标系类型，固定为 "GCJ02" */
        private String coordType;
    }

    /**
     * 时间线节点，构成单日行程的完整时间轴。
     *
     * <p>节点类型包括：DAY_START（出发）、SPOT（景点）、FOOD（餐饮）、 TRANSPORT（交通段）、STAY_AREA（住宿区域）、RENTAL_PICKUP（取车）等。
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TimelineNode {
        /** 节点在当天时间线中的顺序编号 */
        private Integer order;

        /** 节点类型：DAY_START / SPOT / FOOD / TRANSPORT / STAY_AREA / RENTAL_PICKUP */
        private String type;

        /** 开始时间，格式 "HH:mm" */
        private String startTime;

        /** 结束时间，格式 "HH:mm" */
        private String endTime;

        /** 节点主标题（如景点名、餐厅名） */
        private String title;

        /** 节点副标题（如区域名） */
        private String subtitle;

        /** 节点详细描述 */
        private String description;

        /** 所在城市 */
        private String city;

        /** 所在区域/商圈 */
        private String area;

        /** 详细地址 */
        private String address;

        /** 经度 */
        private BigDecimal lng;

        /** 纬度 */
        private BigDecimal lat;

        /** 坐标系类型，固定为 "GCJ02" */
        private String coordType;

        /** 停留/游玩时长（分钟） */
        private Integer durationMinutes;

        /** 时长展示文案，如「2小时」 */
        private String durationText;

        /** 交通方式建议文案（仅 TRANSPORT 类型节点使用） */
        private String transportSuggestion;

        /** 该节点预估费用（元） */
        private Integer estimatedCost;

        /** 费用展示文案 */
        private String costText;

        /** 推荐理由/游览原因 */
        private String reason;

        /** 标签列表，如 ["亲子", "必去"] */
        private List<String> tags;

        /** 是否紧凑展示（无展开详情），用于 DAY_START / TRANSPORT 等辅助节点 */
        private Boolean compact;

        /** 数据来源标识，如 "AMAP"、"AI" */
        private String source;

        /** 交通段的起点景点 order（仅 TRANSPORT 节点） */
        private Integer fromOrder;

        /** 交通段的终点景点 order（仅 TRANSPORT 节点） */
        private Integer toOrder;

        /** 交通段起点锚点名称（仅 TRANSPORT 节点） */
        private String fromAnchor;

        /** 交通段终点锚点名称（仅 TRANSPORT 节点） */
        private String toAnchor;

        /** 住宿节点关联的附近酒店列表（仅 STAY_AREA 节点） */
        private List<NearbyHotel> nearbyHotels;
    }

    /**
     * 景点信息，来自高德 POI 搜索或 AI 规划。
     *
     * <p>包含地理坐标、门票、营业时间、推荐游玩时长等完整景点数据。
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Spot {
        /** 高德 POI ID */
        private String poiId;

        /** 景点名称 */
        private String name;

        /** 景点类型（如景区分类） */
        private String type;

        /** 所在城市 */
        private String city;

        /** 所在区域/商圈 */
        private String area;

        /** 详细地址 */
        private String address;

        /** 经度（GCJ02） */
        private BigDecimal lng;

        /** 纬度（GCJ02） */
        private BigDecimal lat;

        /** 坐标系类型，固定为 "GCJ02" */
        private String coordType;

        /** 景点在当天行程中的游览顺序（从 1 开始） */
        private Integer order;

        /** 建议到达时间，格式 "HH:mm" */
        private String startTime;

        /** 建议游玩时长（分钟） */
        private Integer suggestedDurationMinutes;

        /** 建议游玩时长展示文案，如「2-3小时」 */
        private String suggestedDurationText;

        /** 游玩时长数据来源（如 "AI"、"AMAP"） */
        private String suggestedDurationSource;

        /** 推荐理由 */
        private String reason;

        /** 游玩贴士 */
        private String tips;

        /** 门票价格（元），null 表示未知 */
        private Integer ticketCost;

        /** 门票价格展示文案，如「免费」「约 ¥80」 */
        private String ticketCostText;

        /** 门票价格是否为估算值 */
        private Boolean ticketCostEstimated;

        /** 门票价格数据来源（如 "AMAP"、"AI_ESTIMATE"） */
        private String ticketCostSource;

        /** 营业时间描述，如 "08:00-18:00" */
        private String openingHours;

        /** 高德评分（0-5） */
        private BigDecimal rating;

        /** 人均消费（元） */
        private Integer averageCost;

        /** 所属商圈名称 */
        private String businessArea;

        /** 景点图片 URL 列表 */
        private List<String> imageUrls;

        /** 景点入口经度（GCJ02），用于精确导航 */
        private BigDecimal entranceLng;

        /** 景点入口纬度（GCJ02），用于精确导航 */
        private BigDecimal entranceLat;

        /** 是否需要提前预约/购票 */
        private Boolean reservationRequired;

        /** 景点标签，如 ["5A景区", "必去"] */
        private List<String> tags;

        /** 数据来源标识，如 "AMAP"、"AI" */
        private String source;

        /** AI 推荐置信度（0-1） */
        private BigDecimal confidence;
    }

    /** 路线段，描述两个相邻景点间的交通信息。 */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RouteLeg {
        /** 起点景点的 order 编号 */
        private Integer fromOrder;

        /** 终点景点的 order 编号 */
        private Integer toOrder;

        /** 交通方式，如 "walking"、"driving"、"transit" */
        private String mode;

        /** 交通建议文案（如「建议打车，约 15 分钟」） */
        private String suggestion;

        /** 路程距离（米） */
        private Integer distanceMeters;

        /** 预计耗时（分钟） */
        private Integer durationMinutes;

        /** 预估交通费用（元） */
        private Integer estimatedCost;

        /** 数据来源标识，如 "AMAP"、"AI" */
        private String source;
    }

    /** 餐饮推荐，来自高德 POI 美食搜索或 AI 规划。 */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FoodSuggestion {
        /** 餐厅/美食名称 */
        private String name;

        /** 所在区域/商圈 */
        private String area;

        /** 对应餐次，如 "lunch"、"dinner" */
        private String meal;

        /** 推荐理由 */
        private String reason;

        /** 高德评分（0-5） */
        private BigDecimal rating;

        /** 人均消费（元） */
        private Integer averageCost;

        /** 营业时间描述 */
        private String openingHours;

        /** 数据来源标识，如 "AMAP"、"AI" */
        private String source;

        /** 所在城市 */
        private String city;

        /** 详细地址 */
        private String address;

        /** 经度（GCJ02） */
        private BigDecimal lng;

        /** 纬度（GCJ02） */
        private BigDecimal lat;

        /** 坐标系类型，固定为 "GCJ02" */
        private String coordType;
    }

    /** 单日费用估算汇总（门票 + 餐饮 + 交通）。 */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EstimatedCost {
        /** 当天门票费用合计（元） */
        private Integer tickets;

        /** 当天餐饮费用合计（元） */
        private Integer food;

        /** 当天交通费用合计（元） */
        private Integer transport;

        /** 当天费用总计（元） */
        private Integer total;

        /** 门票费用数据来源说明 */
        private String ticketSource;

        /** 餐饮费用数据来源说明 */
        private String foodSource;

        /** 交通费用数据来源说明 */
        private String transportSource;

        /** 是否排除了来源不明的费用项（true 表示 total 可能偏低） */
        private Boolean excludesUnknownItems;
    }

    /** 全程预算汇总，整合所有天的交通、餐饮、门票及住宿费用。 */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BudgetSummary {
        /** 全程交通费用合计（元） */
        private Integer transportCost;

        /** 全程餐饮费用合计（元） */
        private Integer foodCost;

        /** 全程门票费用合计（元） */
        private Integer ticketCost;

        /** 全程住宿费用合计（元），基于 NearbyHotel 估算价格计算 */
        private Integer hotelCost;

        /** 全程预估总费用（元） */
        private Integer totalEstimatedCost;

        /** 门票费用数据来源说明 */
        private String ticketSource;

        /** 住宿费用数据来源说明 */
        private String hotelSource;

        /** 是否排除了来源不明的费用项（true 表示总计可能偏低） */
        private Boolean excludesUnknownItems;
    }

    /** AI 推荐的住宿区域建议。 */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AccommodationSuggestion {
        /** 推荐住宿区域，如「春熙路/太古里」 */
        private String area;

        /** 推荐该区域的理由 */
        private String reason;

        /** 参考价格区间，如 "¥300-600/晚" */
        private String priceRange;
    }

    /** 数据质量溯源信息，标识各维度数据的主要来源。 */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DataQuality {
        /** POI 数据来源，如 "AMAP"、"AI" */
        private String poiSource;

        /** 路线数据来源，如 "AMAP"、"AI" */
        private String routeSource;

        /** 价格数据来源，如 "AMAP"、"AI_ESTIMATE" */
        private String priceSource;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NearbyHotel {
        /** 酒店名称（高德 POI name） */
        private String name;

        /** 酒店地址（高德 POI address） */
        private String address;

        /** 酒店电话（高德 business.tel） */
        private String tel;

        /** 酒店评分，如 "4.5"（高德 business.rating） */
        private String rating;

        /** 经度（GCJ02 坐标系） */
        private BigDecimal lng;

        /** 纬度（GCJ02 坐标系） */
        private BigDecimal lat;

        /** 坐标系类型，固定为 "GCJ02" */
        private String coordType;

        /** 距搜索中心点的直线距离（米），来自高德 distance 字段 */
        private Integer distanceMeters;

        /** 根据评分和名称关键词估算的参考价格（元/晚） */
        private Integer estimatedCost;

        /** 根据评分和名称关键词估算的价格区间描述，如 "¥250-450/晚" */
        private String estimatedPrice;

        /** 数据来源标识，如 "AMAP_AROUND" */
        private String source;
    }
}
