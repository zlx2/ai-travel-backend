package com.sora.aitravel.workflow;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.action.AsyncEdgeAction;
import com.alibaba.cloud.ai.graph.action.AsyncNodeAction;
import com.alibaba.cloud.ai.graph.action.EdgeAction;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Generate 工作流 — 基于 Spring AI Alibaba StateGraph 的正规编排。
 *
 * <p>按照"先查数据 → 再生成行程 → 再校验事实 → 再给用户确认"的核心流程， 13 个节点串联 + 条件分支 + 逐日循环。
 *
 * <p>当前为骨架版：各节点用 log.info 占位，条件分支的"不通过/不确认"路径 保留路由能力但默认走正常路径校验通过。
 */
@Slf4j
@SuppressWarnings("unchecked")
public class GenerateWorkflowTest {

    // ========== 节点 ID 常量 ==========
    private static final String REQ_LOAD = "requirementLoad";
    private static final String SKELETON = "tripSkeleton";
    private static final String CITY_DATA = "cityDataProfile";
    private static final String DAY_INIT = "dayStateInit";
    private static final String DAY_CONTEXT = "dayContextBuild";
    private static final String DAY_QUERY = "dayQueryPlan";
    private static final String DAY_FETCH = "dayDataFetch";
    private static final String DAY_RANK = "dayDataRank";
    private static final String DAY_GENERATE = "dayPlanGenerate";
    private static final String DAY_VALIDATE = "dayPlanValidate";
    private static final String DAY_REVIEW = "dayReview";
    private static final String DAY_LOCK = "dayLock";
    private static final String SUMMARY = "tripSummary";

    // ========== State Key 常量 ==========
    private static final String KEY_TOTAL_DAYS = "totalDays";
    private static final String KEY_CURRENT_DAY = "currentDay";
    private static final String KEY_VALIDATION_PASSED = "validationPassed";
    private static final String KEY_USER_CONFIRMED = "userConfirmed";
    private static final String KEY_HAS_NEXT_DAY = "hasNextDay";

    private CompiledGraph compiledGraph;

    @BeforeEach
    void buildGraph() throws Exception {
        StateGraph graph = new StateGraph();

        // ──── 1. RequirementLoadNode ────
        graph.addNode(
                REQ_LOAD,
                node_async(
                        state -> {
                            int days = state.value(KEY_TOTAL_DAYS, 3);
                            log.info("[1/13] 正在读取你的旅行需求：目的地=成都，天数={}天，偏好=美食/自然风光", days);
                            log.info("       输入为 Analyze 已确认的结构化数据");
                            return Map.of();
                        }));

        // ──── 2. TripSkeletonNode ────
        graph.addNode(
                SKELETON,
                node_async(
                        state -> {
                            log.info("[2/13] 正在生成整体行程骨架...");
                            log.info("       D1：抵达适应 | D2：城市文化 | D3：自然风光 | D4：轻松收尾");
                            return Map.of();
                        }));

        // ──── 3. CityDataProfileNode ────
        graph.addNode(
                CITY_DATA,
                node_async(
                        state -> {
                            log.info("[3/13] 正在查询成都城市基础数据...");
                            log.info("       查询：热门景点/美食区域/住宿区域/交通枢纽");
                            log.info("       工具：高德 POI 文本搜索 + 周边搜索");
                            return Map.of();
                        }));

        // ──── 4. DayStateInitNode ────
        graph.addNode(
                DAY_INIT,
                node_async(
                        state -> {
                            int totalDays = state.value(KEY_TOTAL_DAYS, 3);
                            log.info("[4/13] 初始化逐日生成状态，总天数={}", totalDays);
                            return Map.of(KEY_CURRENT_DAY, 1);
                        }));

        // ──── 5. DayContextBuildNode ────
        graph.addNode(
                DAY_CONTEXT,
                node_async(
                        state -> {
                            int currentDay = state.value(KEY_CURRENT_DAY, 1);
                            int totalDays = state.value(KEY_TOTAL_DAYS, 3);
                            log.info("[5/13] 准备第 {} 天上下文...", currentDay);
                            log.info("       主题：从骨架匹配当天主题");
                            log.info("       已避开前面锁定的地点");
                            log.info("       剩余天数：{}", totalDays - currentDay);
                            return Map.of();
                        }));

        // ──── 6. DayQueryPlanNode ────
        graph.addNode(
                DAY_QUERY,
                node_async(
                        state -> {
                            int currentDay = state.value(KEY_CURRENT_DAY, 1);
                            log.info("[6/13] 正在制定第 {} 天数据查询计划...", currentDay);
                            log.info("       景点查询 | 美食查询 | 交通查询 | 住宿区域分析");
                            return Map.of();
                        }));

        // ──── 7. DayDataFetchNode ────
        graph.addNode(
                DAY_FETCH,
                node_async(
                        state -> {
                            int currentDay = state.value(KEY_CURRENT_DAY, 1);
                            log.info("[7/13] 正在调用工具查询第 {} 天真实数据...", currentDay);
                            log.info("       高德 POI → 景点/美食/酒店");
                            log.info("       高德路线 → 交通距离/时间");
                            return Map.of();
                        }));

        // ──── 8. DayDataRankNode ────
        graph.addNode(
                DAY_RANK,
                node_async(
                        state -> {
                            log.info("[8/13] 数据清洗、去重、筛选、排序...");
                            log.info("       过滤掉与前面天数重复的地点");
                            log.info("       按距离/评分/顺路程度排序");
                            return Map.of();
                        }));

        // ──── 9. DayPlanGenerateNode ────
        graph.addNode(
                DAY_GENERATE,
                node_async(
                        state -> {
                            int currentDay = state.value(KEY_CURRENT_DAY, 1);
                            log.info("[9/13] AI 基于真实数据生成第 {} 天行程...", currentDay);
                            log.info(
                                    "       AI 只能使用 scenicCandidates/foodCandidates/hotelContext/transportRoutes");
                            log.info("       不编造具体地点、开放时间、门票价格");
                            return Map.of();
                        }));

        // ──── 10. DayPlanValidateNode ────
        graph.addNode(
                DAY_VALIDATE,
                node_async(
                        state -> {
                            int currentDay = state.value(KEY_CURRENT_DAY, 1);
                            log.info("[10/13] 正在校验第 {} 天行程...", currentDay);
                            log.info("        事实校验：所有地点是否来自工具数据");
                            log.info("        路线校验：是否跨度过大/来回折返");
                            log.info("        体验校验：是否符合偏好/节奏");
                            // 骨架模式：默认校验通过
                            boolean passed = true;
                            log.info("        校验结果：{}", passed ? "通过 ✅" : "未通过 ❌");
                            return Map.of(KEY_VALIDATION_PASSED, passed);
                        }));

        // ──── 11. DayReviewNode (HITL 中断占位) ────
        graph.addNode(
                DAY_REVIEW,
                node_async(
                        state -> {
                            int currentDay = state.value(KEY_CURRENT_DAY, 1);
                            log.info("[11/13] 展示第 {} 天行程给用户确认...", currentDay);
                            log.info("        [HITL] 此处应中断等待用户确认/修改");
                            log.info("        用户可查看：时间块/景点/美食/交通/预算/提示");
                            // 骨架模式：默认用户确认
                            boolean confirmed = true;
                            log.info("        用户反馈：{}", confirmed ? "确认 ✅" : "需要修改");
                            return Map.of(KEY_USER_CONFIRMED, confirmed);
                        }));

        // ──── 12. DayLockNode ────
        graph.addNode(
                DAY_LOCK,
                node_async(
                        state -> {
                            int currentDay = state.value(KEY_CURRENT_DAY, 1);
                            int totalDays = state.value(KEY_TOTAL_DAYS, 3);
                            log.info("[12/13] 第 {} 天行程已锁定！", currentDay);
                            if (currentDay < totalDays) {
                                int nextDay = currentDay + 1;
                                log.info("        还有第 {} 天，继续生成...", nextDay);
                                return Map.of(KEY_CURRENT_DAY, nextDay, KEY_HAS_NEXT_DAY, true);
                            } else {
                                log.info("        所有天数已锁定，生成完整总览...");
                                return Map.of(KEY_HAS_NEXT_DAY, false);
                            }
                        }));

        // ──── 13. TripSummaryNode ────
        graph.addNode(
                SUMMARY,
                node_async(
                        state -> {
                            log.info("[13/13] 正在生成完整行程总览...");
                            log.info("        汇总所有锁定的天数");
                            log.info("        生成结构化 tripPlan JSON");
                            log.info("        包含：总览/每日详情/住宿建议/预算估算/数据来源");
                            log.info("        ───────────────────────");
                            log.info("        ✅ 行程生成完成！返回 tripPlan");
                            return Map.of();
                        }));

        // ══════════════════════════════════════
        // 边：编排流程
        // ══════════════════════════════════════

        // 直线流程：START → 1→2→3→4→5→6→7→8→9
        graph.addEdge(StateGraph.START, REQ_LOAD);
        graph.addEdge(REQ_LOAD, SKELETON);
        graph.addEdge(SKELETON, CITY_DATA);
        graph.addEdge(CITY_DATA, DAY_INIT);
        graph.addEdge(DAY_INIT, DAY_CONTEXT);
        graph.addEdge(DAY_CONTEXT, DAY_QUERY);
        graph.addEdge(DAY_QUERY, DAY_FETCH);
        graph.addEdge(DAY_FETCH, DAY_RANK);
        graph.addEdge(DAY_RANK, DAY_GENERATE);
        graph.addEdge(DAY_GENERATE, DAY_VALIDATE);

        // 条件分支 ①：校验 → 通过=review / 不通过=generate（重新生成当天）
        graph.addConditionalEdges(
                DAY_VALIDATE,
                edge_async(
                        state -> {
                            boolean passed = state.value(KEY_VALIDATION_PASSED, true);
                            return passed ? DAY_REVIEW : DAY_GENERATE;
                        }),
                Map.of(DAY_REVIEW, DAY_REVIEW, DAY_GENERATE, DAY_GENERATE));

        // 条件分支 ②：用户确认 → 确认=lock / 不确认=context（重新构建当天）
        graph.addConditionalEdges(
                DAY_REVIEW,
                edge_async(
                        state -> {
                            boolean confirmed = state.value(KEY_USER_CONFIRMED, true);
                            return confirmed ? DAY_LOCK : DAY_CONTEXT;
                        }),
                Map.of(DAY_LOCK, DAY_LOCK, DAY_CONTEXT, DAY_CONTEXT));

        // 条件分支 ③：还有下一天？→ 有=context（循环）/ 无=summary（结束）
        graph.addConditionalEdges(
                DAY_LOCK,
                edge_async(
                        state -> {
                            boolean hasNext = state.value(KEY_HAS_NEXT_DAY, true);
                            return hasNext ? DAY_CONTEXT : SUMMARY;
                        }),
                Map.of(DAY_CONTEXT, DAY_CONTEXT, SUMMARY, SUMMARY));

        // 总览 → END
        graph.addEdge(SUMMARY, StateGraph.END);

        compiledGraph = graph.compile();
        log.info("========== Generate 工作流编译完成 ==========");
    }

    /**
     * 运行完整工作流——3天行程生成。
     *
     * <p>预期输出：13 个节点按顺序执行，逐日循环 3 次，最后进入总览。
     */
    @Test
    void testThreeDayGenerate() throws Exception {
        log.info("========== 开始生成 3 天成都行程 ==========");

        compiledGraph.stream(
                        Map.of(
                                KEY_TOTAL_DAYS, 3,
                                KEY_CURRENT_DAY, 0))
                .doOnNext(node -> log.info("   >>> 进入节点：{}", node.node()))
                .blockLast();

        log.info("========== 工作流执行完毕 ==========");
    }

    /** 1 天行程——快速验证骨架。 */
    @Test
    void testOneDayGenerate() throws Exception {
        log.info("========== 开始生成 1 天成都行程 ==========");

        compiledGraph.stream(
                        Map.of(
                                KEY_TOTAL_DAYS, 1,
                                KEY_CURRENT_DAY, 0))
                .doOnNext(node -> log.info("   >>> 进入节点：{}", node.node()))
                .blockLast();

        log.info("========== 工作流执行完毕 ==========");
    }

    // ========== 包装辅助 ==========

    private static AsyncNodeAction node_async(NodeAction action) {
        return AsyncNodeAction.node_async(action);
    }

    /** EdgeAction → AsyncEdgeAction 包装 */
    private static AsyncEdgeAction edge_async(EdgeAction action) {
        return AsyncEdgeAction.edge_async(action);
    }
}
