package com.sora.aitravel.workflow.generate;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.sora.aitravel.workflow.AlibabaGraphWorkflow;
import java.util.List;
import java.util.function.BiConsumer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class TripGenerateWorkflow {

    private final CompiledGraph graph;
    private final GenerateResponseNormalizer responseNormalizer;
    private final List<AlibabaGraphWorkflow.Step<GenerateWorkflowContext>> steps;

    public TripGenerateWorkflow(
            RequirementValidateNode requirementValidateNode,
            RequirementLoadNode requirementLoadNode,
            CityDataProfileNode cityDataProfileNode,
            CandidatePoolBuildNode candidatePoolBuildNode,
            AiMacroRoutePlanNode aiMacroRoutePlanNode,
            AmapMacroRouteFactNode amapMacroRouteFactNode,
            AiRouteCriticNode aiRouteCriticNode,
            MacroRouteContractValidateNode macroRouteContractValidateNode,
            WeatherFetchNode weatherFetchNode,
            HotelFetchNode hotelFetchNode,
            DayStateInitNode dayStateInitNode,
            DayContextBuildNode dayContextBuildNode,
            DayQueryPlanNode dayQueryPlanNode,
            FoodRecommendNode foodRecommendNode,
            DayDataFetchNode dayDataFetchNode,
            DayDataRankNode dayDataRankNode,
            DayPlanGenerateNode dayPlanGenerateNode,
            TripTimelineAssembler tripTimelineAssembler,
            DayPlanValidateNode dayPlanValidateNode,
            TripSummaryNode tripSummaryNode,
            GenerateResultMergeNode generateResultMergeNode,
            GenerateResponseNormalizer responseNormalizer) {
        this.responseNormalizer = responseNormalizer;
        this.steps =
                List.of(
                        AlibabaGraphWorkflow.step(
                                "requirement-validate", requirementValidateNode::execute),
                        AlibabaGraphWorkflow.step("requirement-load", requirementLoadNode::execute),
                        AlibabaGraphWorkflow.step(
                                "city-data-profile", cityDataProfileNode::execute),
                        AlibabaGraphWorkflow.step(
                                "candidate-pool-build", candidatePoolBuildNode::execute),
                        AlibabaGraphWorkflow.step(
                                "ai-macro-route-plan", aiMacroRoutePlanNode::execute),
                        AlibabaGraphWorkflow.step(
                                "amap-macro-route-fact", amapMacroRouteFactNode::execute),
                        AlibabaGraphWorkflow.step("ai-route-critic", aiRouteCriticNode::execute),
                        AlibabaGraphWorkflow.step(
                                "macro-route-contract-validate",
                                macroRouteContractValidateNode::execute),
                        AlibabaGraphWorkflow.step("weather-fetch", weatherFetchNode::execute),
                        AlibabaGraphWorkflow.step("hotel-fetch", hotelFetchNode::execute),
                        AlibabaGraphWorkflow.step("day-state-init", dayStateInitNode::execute),
                        AlibabaGraphWorkflow.step(
                                "day-context-build", dayContextBuildNode::execute),
                        AlibabaGraphWorkflow.step("day-query-plan", dayQueryPlanNode::execute),
                        AlibabaGraphWorkflow.step("food-recommend", foodRecommendNode::execute),
                        AlibabaGraphWorkflow.step("day-data-fetch", dayDataFetchNode::execute),
                        AlibabaGraphWorkflow.step("day-data-rank", dayDataRankNode::execute),
                        AlibabaGraphWorkflow.step(
                                "day-plan-generate", dayPlanGenerateNode::execute),
                        AlibabaGraphWorkflow.step(
                                "trip-timeline-assemble", tripTimelineAssembler::execute),
                        AlibabaGraphWorkflow.step(
                                "day-plan-validate", dayPlanValidateNode::execute),
                        AlibabaGraphWorkflow.step("trip-summary", tripSummaryNode::execute),
                        AlibabaGraphWorkflow.step(
                                "result-merge", generateResultMergeNode::execute));
        this.graph = AlibabaGraphWorkflow.compile("trip-generate-workflow", steps);
    }

    public GenerateWorkflowContext execute(GenerateWorkflowContext context) {
        long start = WorkflowTiming.start();
        try {
            GenerateWorkflowContext result = runSteps(context, null);
            log.info(
                    "行程生成总耗时 workflow=trip-generate-workflow elapsedMs={}",
                    WorkflowTiming.elapsedMs(start));
            return result;
        } catch (RuntimeException exception) {
            log.info(
                    "行程生成总耗时 workflow=trip-generate-workflow status=failed elapsedMs={}",
                    WorkflowTiming.elapsedMs(start));
            throw exception;
        }
    }

    public GenerateWorkflowContext executeWithProgress(
            GenerateWorkflowContext context, BiConsumer<String, Integer> progress) {
        long start = WorkflowTiming.start();
        try {
            GenerateWorkflowContext result = runSteps(context, progress);
            log.info(
                    "行程生成总耗时 workflow=trip-generate-workflow elapsedMs={}",
                    WorkflowTiming.elapsedMs(start));
            return result;
        } catch (RuntimeException exception) {
            log.info(
                    "行程生成总耗时 workflow=trip-generate-workflow status=failed elapsedMs={}",
                    WorkflowTiming.elapsedMs(start));
            throw exception;
        }
    }

    private GenerateWorkflowContext runSteps(
            GenerateWorkflowContext context, BiConsumer<String, Integer> progress) {
        int total = steps.size();
        for (int index = 0; index < total; index++) {
            AlibabaGraphWorkflow.Step<GenerateWorkflowContext> step = steps.get(index);
            if (progress != null) {
                progress.accept(step.getName(), Math.max(1, (index * 100) / total));
            }
            try {
                long nodeStart = WorkflowTiming.start();
                try {
                    step.getAction().execute(context);
                } finally {
                    log.info(
                            "行程生成耗时 workflow=trip-generate-workflow node={} elapsedMs={}",
                            step.getName(),
                            WorkflowTiming.elapsedMs(nodeStart));
                }
            } catch (Exception ex) {
                throw new IllegalStateException(
                        "Trip generate workflow node failed: " + step.getName(), ex);
            }
            if (progress != null) {
                progress.accept(step.getName(), Math.min(99, ((index + 1) * 100) / total));
            }
        }
        WorkflowTiming.run("trip-generate-workflow", "response-normalize", () -> responseNormalizer.normalize(context));
        return context;
    }
}
