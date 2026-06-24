package com.sora.aitravel.tools;

import cn.hutool.http.HttpRequest;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 酒店查询工具 —— 通过高德地图 POI 搜索 API 获取真实酒店数据。
 *
 * <p>高德开放平台：https://lbs.amap.com/ POI 搜索文档：https://lbs.amap.com/api/webservice/guide/api/search
 * 酒店类型编码：100100（宾馆酒店）
 */
@Component
public class HotelTool {

    @Value("${app.amap.api-key:}")
    private String amapApiKey;

    @Value("${app.amap.base-url:https://restapi.amap.com}")
    private String amapBaseUrl;

    /** 常见酒店品牌 → 价格区间（元/晚）。 基于国内主流预订平台（携程/美团）的常见价格。 */
    private static final Map<String, int[]> BRAND_PRICES =
            Map.ofEntries(
                    // 经济连锁
                    Map.entry("如家", new int[] {120, 250}),
                    Map.entry("汉庭", new int[] {150, 300}),
                    Map.entry("7天", new int[] {100, 220}),
                    Map.entry("锦江", new int[] {130, 280}),
                    Map.entry("城市便捷", new int[] {120, 260}),
                    Map.entry("维也纳", new int[] {180, 350}),
                    Map.entry("全季", new int[] {200, 380}),
                    Map.entry("亚朵", new int[] {280, 500}),
                    Map.entry("OYO", new int[] {80, 200}),
                    Map.entry("格林豪泰", new int[] {100, 230}),
                    Map.entry("速8", new int[] {110, 240}),
                    Map.entry("宜必思", new int[] {180, 350}),
                    Map.entry("丽枫", new int[] {200, 380}),
                    Map.entry("桔子", new int[] {180, 350}),
                    Map.entry("漫心", new int[] {250, 450}),
                    // 中高档
                    Map.entry("希尔顿", new int[] {500, 1200}),
                    Map.entry("万豪", new int[] {600, 1500}),
                    Map.entry("洲际", new int[] {500, 1200}),
                    Map.entry("香格里拉", new int[] {600, 1500}),
                    Map.entry("凯悦", new int[] {500, 1200}),
                    Map.entry("喜来登", new int[] {500, 1200}),
                    Map.entry("威斯汀", new int[] {500, 1200}),
                    Map.entry("索菲特", new int[] {500, 1200}),
                    Map.entry("皇冠假日", new int[] {450, 1000}),
                    Map.entry("假日", new int[] {350, 700}),
                    Map.entry("美居", new int[] {300, 600}),
                    Map.entry("诺富特", new int[] {350, 700}),
                    // 豪华
                    Map.entry("丽思卡尔顿", new int[] {1200, 3000}),
                    Map.entry("半岛", new int[] {1500, 3500}),
                    Map.entry("安缦", new int[] {2000, 5000}),
                    Map.entry("宝格丽", new int[] {2000, 5000}),
                    Map.entry("华尔道夫", new int[] {1200, 3000}),
                    Map.entry("柏悦", new int[] {1000, 2500}),
                    Map.entry("瑞吉", new int[] {1000, 2500}),
                    Map.entry("文华东方", new int[] {1200, 3000}),
                    Map.entry("四季", new int[] {1200, 3000}),
                    Map.entry("瑰丽", new int[] {1000, 2500}),
                    Map.entry("亚特兰蒂斯", new int[] {1500, 4000}));

    @Tool(description = "搜索指定城市的酒店信息，包括酒店名称、地址、联系电话、评分、价格区间等。" + "当用户询问某个城市住哪里、推荐酒店、酒店价格时调用此工具。")
    public String searchHotel(
            @ToolParam(description = "城市名称，例如：北京、上海、三亚") String city,
            @ToolParam(description = "入住日期，格式 yyyy-MM-dd，例如 2025-07-01") String checkInDate,
            @ToolParam(description = "离店日期，格式 yyyy-MM-dd，例如 2025-07-03") String checkOutDate) {

        try {
            // 计算入住天数
            LocalDate in = LocalDate.parse(checkInDate);
            LocalDate out = LocalDate.parse(checkOutDate);
            long nights = ChronoUnit.DAYS.between(in, out);
            if (nights <= 0) nights = 1;

            // 第一步：调用高德 POI 搜索 API 获取酒店列表
            String searchUrl = amapBaseUrl + "/v3/place/text";
            String searchResponse =
                    HttpRequest.get(searchUrl)
                            .form("key", amapApiKey) // API密钥，用于身份验证
                            .form("keywords", "酒店") // 搜索关键词：酒店
                            .form("city", city) // 动态搜索城市，如"北京"、"上海"
                            .form("citylimit", "true") // 强制城市范围限制，只返回指定城市的结果
                            .form("types", "100100") // POI类型编码：100100代表宾馆酒店
                            .form("offset", "8") // 每页返回数量：8条记录
                            .form("page", "1") // 页码：第1页
                            .form("extensions", "all") // 扩展信息：返回完整详细信息（包括评分、电话等）
                            .timeout(10000) // 请求超时时间：10秒（10000毫秒）
                            .execute() // 执行HTTP GET请求
                            .body(); // 获取响应体内容（JSON字符串）

            JSONObject searchJson = JSONUtil.parseObj(searchResponse);
            if (!"1".equals(searchJson.getStr("status"))) {
                return "高德 API 调用失败：" + searchJson.getStr("info");
            }

            JSONArray pois = searchJson.getJSONArray("pois");
            if (pois == null || pois.isEmpty()) {
                return "未找到 " + city + " 的酒店信息，建议尝试其他城市名称。";
            }

            // 第二步：获取酒店详情并估算价格
            StringBuilder sb = new StringBuilder();
            sb.append("【")
                    .append(city)
                    .append(" 酒店推荐】")
                    .append("（入住：")
                    .append(checkInDate)
                    .append("，离店：")
                    .append(checkOutDate)
                    .append("，共")
                    .append(nights)
                    .append("晚）\n\n");

            int count = Math.min(pois.size(), 6);
            for (int i = 0; i < count; i++) {
                JSONObject poi = pois.getJSONObject(i);
                // 从POI数据中提取酒店基本信息字段
                String poiId = poi.getStr("id"); // 酒店POI唯一标识符，用于后续获取详情
                String name = poi.getStr("name"); // 酒店名称
                String address = poi.getStr("address"); // 酒店详细地址
                String tel = poi.getStr("tel"); // 酒店联系电话
                String rating = poi.getStr("biz_ext_rating"); // 酒店评分（扩展信息字段）

                // 获取酒店详情
                JSONObject detail = fetchHotelDetail(poiId);
                String type = detail != null ? detail.getStr("type") : poi.getStr("type");

                // 基于品牌 + 类型估算价格
                String priceInfo = estimatePrice(name, type, city);

                sb.append(i + 1).append(". ").append(name).append("\n");
                if (address != null && !address.isEmpty()) {
                    sb.append("   地址：").append(address).append("\n");
                }
                if (tel != null && !tel.isEmpty()) {
                    sb.append("   电话：").append(tel).append("\n");
                }
                if (rating != null && !rating.isEmpty()) {
                    sb.append("   评分：").append(rating).append("/5.0\n");
                }
                if (type != null && !type.isEmpty()) {
                    sb.append("   类型：").append(type).append("\n");
                }
                sb.append("   价格：").append(priceInfo).append("\n");

                int[] prices = parsePriceRange(priceInfo);
                if (prices != null) {
                    int minTotal = prices[0] * (int) nights;
                    int maxTotal = prices[1] * (int) nights;
                    sb.append("   预估总价：¥")
                            .append(minTotal)
                            .append(" ~ ¥")
                            .append(maxTotal)
                            .append("（")
                            .append(nights)
                            .append("晚）\n");
                }
                sb.append("\n");
            }

            sb.append("数据来源：高德地图。价格为参考区间，实际价格以携程/美团等预订平台为准。");
            return sb.toString();
        } catch (Exception e) {
            return "查询 " + city + " 酒店失败：" + e.getMessage();
        }
    }

    /** 获取酒店详情。 */
    private JSONObject fetchHotelDetail(String poiId) {
        try {
            String detailUrl = amapBaseUrl + "/v3/place/detail";
            String response =
                    HttpRequest.get(detailUrl)
                            .form("key", amapApiKey)
                            .form("id", poiId)
                            .form("extensions", "all")
                            .timeout(8000)
                            .execute()
                            .body();

            JSONObject json = JSONUtil.parseObj(response);
            if (!"1".equals(json.getStr("status"))) {
                return null;
            }
            JSONArray pois = json.getJSONArray("pois");
            if (pois == null || pois.isEmpty()) {
                return null;
            }
            return pois.getJSONObject(0);
        } catch (Exception e) {
            return null;
        }
    }

    /** 基于酒店名称（品牌识别）+ 类型 + 城市估算价格区间。 */
    private String estimatePrice(String name, String type, String city) {
        if (name == null) name = "";

        // 1. 优先通过品牌匹配价格
        for (Map.Entry<String, int[]> entry : BRAND_PRICES.entrySet()) {
            if (name.contains(entry.getKey())) {
                int[] range = entry.getValue();
                int[] adjusted = adjustByCity(range, city);
                return adjusted[0] + "-" + adjusted[1] + " 元/晚";
            }
        }

        // 2. 通过名称关键词判断档次
        if (name.contains("五星级")
                || name.contains("豪华")
                || name.contains("度假别墅")
                || name.contains("庄园")
                || name.contains("行宫")) {
            return "800-3000 元/晚（豪华型）";
        }
        if (name.contains("四星") || name.contains("高档") || name.contains("精品")) {
            return "400-800 元/晚（高档型）";
        }
        if (name.contains("三星") || name.contains("舒适") || name.contains("商务")) {
            return "200-400 元/晚（舒适型）";
        }
        if (name.contains("公寓")
                || name.contains("民宿")
                || name.contains("旅租")
                || name.contains("青旅")
                || name.contains("青年")
                || name.contains("自助")) {
            return "80-200 元/晚（经济型）";
        }

        // 3. 通过 type 字段判断
        if (type != null) {
            if (type.contains("五星级") || type.contains("豪华")) {
                return "800-3000 元/晚（豪华型）";
            }
            if (type.contains("四星级") || type.contains("高档")) {
                return "400-800 元/晚（高档型）";
            }
            if (type.contains("三星级") || type.contains("舒适")) {
                return "200-400 元/晚（舒适型）";
            }
            if (type.contains("经济") || type.contains("连锁")) {
                return "100-300 元/晚（经济型）";
            }
        }

        // 4. 默认价格（普通宾馆酒店）
        int[] defaultRange = adjustByCity(new int[] {150, 350}, city);
        return defaultRange[0] + "-" + defaultRange[1] + " 元/晚";
    }

    /** 根据城市等级调整价格（一线城市价格上浮）。 */
    private int[] adjustByCity(int[] baseRange, String city) {
        if (city == null) return baseRange;
        // 一线/热门旅游城市价格上浮 30%-50%
        if ("北京".contains(city)
                || "上海".contains(city)
                || "深圳".contains(city)
                || "广州".contains(city)
                || "三亚".contains(city)) {
            return new int[] {(int) (baseRange[0] * 1.4), (int) (baseRange[1] * 1.4)};
        }
        // 新一线/热门城市上浮 15%-20%
        if ("成都".contains(city)
                || "杭州".contains(city)
                || "重庆".contains(city)
                || "南京".contains(city)
                || "武汉".contains(city)
                || "西安".contains(city)) {
            return new int[] {(int) (baseRange[0] * 1.2), (int) (baseRange[1] * 1.2)};
        }
        return baseRange;
    }

    /** 解析价格区间字符串，返回 [最低价, 最高价]。 */
    private int[] parsePriceRange(String priceInfo) {
        try {
            String digits = priceInfo.replaceAll("[^0-9\\-]", "");
            String[] parts = digits.split("-");
            int min = Integer.parseInt(parts[0].trim());
            int max = parts.length > 1 ? Integer.parseInt(parts[1].trim()) : min;
            return new int[] {min, max};
        } catch (Exception e) {
            return null;
        }
    }
}
