package com.sora.aitravel.config;

import static org.assertj.core.api.Assertions.assertThat;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/** Real DeepSeek latency smoke test. Opt in with DEEPSEEK_LATENCY_TEST=true. */
@EnabledIfEnvironmentVariable(named = "DEEPSEEK_LATENCY_TEST", matches = "true")
class DeepSeekLatencySmokeTest {

    private final String apiKey = System.getenv("DEEPSEEK_API_KEY");
    private final String baseUrl =
            System.getenv()
                    .getOrDefault("DEEPSEEK_BASE_URL", "https://api.deepseek.com")
                    .replaceAll("/+$", "");
    private final String model = System.getenv().getOrDefault("DEEPSEEK_MODEL", "deepseek-v4-flash");
    private final HttpClient httpClient =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    @Test
    void deepseekV4FlashNoThinkingReturnsValidTripJsonFastEnough() throws Exception {
        assertThat(apiKey).isNotBlank();
        List<Probe> probes =
                List.of(
                        new Probe("route", routePrompt(), "days"),
                        new Probe("scenic", scenicPrompt(), "places"),
                        new Probe("day-plan", dayPlanPrompt(), "selected"));

        for (Probe probe : probes) {
            ProbeResult result = call(probe);
            System.out.printf(
                    "DeepSeek latency probe scene=%s elapsed=%dms promptLength=%d responseLength=%d sample=%s%n",
                    probe.name(),
                    result.elapsedMs(),
                    probe.prompt().length(),
                    result.content().length(),
                    abbreviate(result.content()));

            assertThat(result.status()).isEqualTo(200);
            assertThat(result.json().containsKey(probe.requiredField())).isTrue();
            assertThat(result.elapsedMs()).isLessThan(6_000);
        }
    }

    private ProbeResult call(Probe probe) throws Exception {
        JSONArray messages = new JSONArray();
        messages.add(JSONUtil.createObj().set("role", "user").set("content", probe.prompt()));
        JSONObject body =
                JSONUtil.createObj()
                        .set("model", model)
                        .set("messages", messages)
                        .set("temperature", 0.15)
                        .set("max_tokens", 900)
                        .set("response_format", JSONUtil.createObj().set("type", "json_object"))
                        .set("thinking", JSONUtil.createObj().set("type", "disabled"));

        long start = System.currentTimeMillis();
        HttpRequest request =
                HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl + "/chat/completions"))
                        .timeout(Duration.ofSeconds(20))
                        .header("Authorization", "Bearer " + apiKey)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                        .build();
        HttpResponse<String> response =
                httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        long elapsed = System.currentTimeMillis() - start;
        JSONObject root = JSONUtil.parseObj(response.body());
        String content =
                root.getJSONArray("choices")
                        .getJSONObject(0)
                        .getJSONObject("message")
                        .getStr("content")
                        .replaceFirst("^\\s*</think>\\s*", "")
                        .trim();
        return new ProbeResult(response.statusCode(), elapsed, content, JSONUtil.parseObj(content));
    }

    private String routePrompt() {
        return """
                规划 杭州 3 天游的每日主锚点。偏好=上海出发，带父母同行，杭州东站到达并取车，同城还车；每天 09:30 后开始，午餐本地菜，晚餐后轻松夜游；父母不想爬山太多，喜欢西湖自然风光、历史文化、博物馆和老街美食；预算 4000 元以内，节奏轻松，避免一天内东西横跳。到达/取车=杭州东站；租车=舒适型轿车。
                规则：每天 1 个 mainPlace，2-4 个 anchorPlaces；都必须是真实可搜景区/街区/公园/博物馆/古镇/片区；不要泛词和附属设施；Day1 顺路轻量，后续按方向推进不折返。只返回 JSON：
                {"days":[{"day":1,"city":"杭州","theme":"市区轻量游","direction":"从到达点向西湖周边推进","mainPlace":"西湖风景名胜区","anchorPlaces":["西湖风景名胜区","河坊街","南宋御街"]}]}
                """;
    }

    private String scenicPrompt() {
        return """
                为单日旅行推荐 8-12 个真实地点名，用于高德 POI 搜索。
                城市=成都；day=2；主片区=青羊区/武侯区；主题=历史文化和城市烟火；偏好=上海飞成都，双流机场取车，3 天游，喜欢历史文化、川菜美食、茶馆、公园、夜间散步，节奏轻松，不想去过度商业化和太偏的点；已用=宽窄巷子、人民公园；租车=舒适型轿车，同城还车；修改=无
                规则：只给游客会去的景点/街区/公园/博物馆/古镇/自然景区；不要泛词、餐厅、酒店、商场、停车场、入口、游客中心、厕所、管理处；不要拆同一景区内部小点；尽量覆盖 2-3 个游览单元；避开已用片区。只返回 JSON：
                {"places":["地点1","地点2"]}
                """;
    }

    private String dayPlanPrompt() {
        return """
                从候选中为单日行程选点并写短推荐理由。城市=西安；day=1；主题=古都初体验和夜景；数量=3；偏好=上海出发，高铁到西安北站，租车同城还车，喜欢历史文化、博物馆、古城墙、本地小吃，节奏轻松；租车=已租车，但市区停车不方便时少开车；修改=无。
                候选：
                c1|西安城墙|历史文化|碑林区|适合下午登城墙看古城格局
                c2|陕西历史博物馆|博物馆|雁塔区|热门博物馆，适合提前预约
                c3|大雁塔文化休闲景区|历史文化|雁塔区|适合傍晚和夜景
                c4|大唐不夜城|夜景|雁塔区|晚餐后步行夜游
                c5|回民街|美食街区|莲湖区|游客多但吃饭方便
                c6|华清宫|历史文化|临潼区|距离较远，适合单独半天

                规则：必须选 3 个，只能用候选 id，不能新增地点；理由写游客能看到/体验到什么，45-80 字，别写评分/门票/开放时间/距离，避免空话。只返回 JSON：
                {"selected":[{"id":"c1","reason":"推荐理由"}]}
                """;
    }

    private String abbreviate(String value) {
        return value.length() <= 180 ? value : value.substring(0, 180) + "...";
    }

    private record Probe(String name, String prompt, String requiredField) {}

    private record ProbeResult(int status, long elapsedMs, String content, JSONObject json) {}
}
