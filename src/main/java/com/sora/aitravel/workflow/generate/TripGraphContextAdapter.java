package com.sora.aitravel.workflow.generate;

import static com.sora.aitravel.workflow.generate.state.TripGraphStateKeys.CANDIDATE_POOL;
import static com.sora.aitravel.workflow.generate.state.TripGraphStateKeys.CITY_PROFILE;
import static com.sora.aitravel.workflow.generate.state.TripGraphStateKeys.DAY_CONTEXTS;
import static com.sora.aitravel.workflow.generate.state.TripGraphStateKeys.DAY_QUERY_PLANS;
import static com.sora.aitravel.workflow.generate.state.TripGraphStateKeys.DAY_SKELETONS;
import static com.sora.aitravel.workflow.generate.state.TripGraphStateKeys.DAY_VALIDATION_REPORTS;
import static com.sora.aitravel.workflow.generate.state.TripGraphStateKeys.FOOD_RECOMMENDATIONS_BY_DAY;
import static com.sora.aitravel.workflow.generate.state.TripGraphStateKeys.HOTEL_SEARCH_RESULT;
import static com.sora.aitravel.workflow.generate.state.TripGraphStateKeys.LOCKED_DAILY_PLANS;
import static com.sora.aitravel.workflow.generate.state.TripGraphStateKeys.MACRO_ROUTE_FACTS;
import static com.sora.aitravel.workflow.generate.state.TripGraphStateKeys.MACRO_ROUTE_PLANS;
import static com.sora.aitravel.workflow.generate.state.TripGraphStateKeys.PREVIOUS_DAILY_PLANS;
import static com.sora.aitravel.workflow.generate.state.TripGraphStateKeys.RANKED_DAY_DATA_PACKAGES;
import static com.sora.aitravel.workflow.generate.state.TripGraphStateKeys.RECOMMENDATION_CONTEXT;
import static com.sora.aitravel.workflow.generate.state.TripGraphStateKeys.RENTAL_TRIP_CONTEXT;
import static com.sora.aitravel.workflow.generate.state.TripGraphStateKeys.REQUEST;
import static com.sora.aitravel.workflow.generate.state.TripGraphStateKeys.REQUIREMENT;
import static com.sora.aitravel.workflow.generate.state.TripGraphStateKeys.RESULT;
import static com.sora.aitravel.workflow.generate.state.TripGraphStateKeys.REVISION_TEXT;
import static com.sora.aitravel.workflow.generate.state.TripGraphStateKeys.ROUTE_CRITIC_RESULT;
import static com.sora.aitravel.workflow.generate.state.TripGraphStateKeys.SELECTED_QUOTE;
import static com.sora.aitravel.workflow.generate.state.TripGraphStateKeys.SINGLE_DAY_GENERATION;
import static com.sora.aitravel.workflow.generate.state.TripGraphStateKeys.TARGET_DAY_NO;
import static com.sora.aitravel.workflow.generate.state.TripGraphStateKeys.USER_ID;
import static com.sora.aitravel.workflow.generate.state.TripGraphStateKeys.WEATHER_FORECAST;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.fasterxml.jackson.core.type.TypeReference;
import com.sora.aitravel.dto.model.RecommendationContextDTO;
import com.sora.aitravel.dto.model.RentalQuoteOptionDTO;
import com.sora.aitravel.dto.model.RentalTripContextDTO;
import com.sora.aitravel.dto.model.TravelRequirementDTO;
import com.sora.aitravel.dto.model.TripPlanDTO;
import com.sora.aitravel.dto.request.TripGenerateRequest;
import com.sora.aitravel.dto.response.FoodRecommendResponse;
import com.sora.aitravel.dto.response.TripGenerateResponse;
import com.sora.aitravel.workflow.generate.state.TripGraphStateCodec;
import java.util.List;
import java.util.Map;

/** Adapter between native graph state keys and legacy node context objects. */
final class TripGraphContextAdapter {
    private static final TypeReference<List<DaySkeleton>> DAY_SKELETON_LIST = new TypeReference<>() {};
    private static final TypeReference<List<MacroRoutePlan>> MACRO_ROUTE_PLAN_LIST = new TypeReference<>() {};
    private static final TypeReference<List<MacroRouteFact>> MACRO_ROUTE_FACT_LIST = new TypeReference<>() {};
    private static final TypeReference<List<DayContext>> DAY_CONTEXT_LIST = new TypeReference<>() {};
    private static final TypeReference<List<DayQueryPlan>> DAY_QUERY_PLAN_LIST = new TypeReference<>() {};
    private static final TypeReference<Map<Integer, FoodRecommendResponse>> FOOD_MAP = new TypeReference<>() {};
    private static final TypeReference<List<DayDataPackage>> DAY_DATA_PACKAGE_LIST = new TypeReference<>() {};
    private static final TypeReference<List<DayPlanValidationReport>> DAY_VALIDATION_REPORT_LIST =
            new TypeReference<>() {};
    private static final TypeReference<List<TripPlanDTO.DailyPlan>> DAILY_PLAN_LIST = new TypeReference<>() {};

    private TripGraphContextAdapter() {}

    static GenerateWorkflowContext fromState(OverAllState state) {
        GenerateWorkflowContext context = new GenerateWorkflowContext();
        context.setUserId(optional(state, USER_ID, Long.class));
        context.setRequest(optional(state, REQUEST, TripGenerateRequest.class));
        context.setRequirement(optional(state, REQUIREMENT, TravelRequirementDTO.class));
        context.setSelectedQuote(optional(state, SELECTED_QUOTE, RentalQuoteOptionDTO.class));
        context.setRentalTripContext(optional(state, RENTAL_TRIP_CONTEXT, RentalTripContextDTO.class));
        context.setCityProfile(optional(state, CITY_PROFILE, CityProfile.class));
        context.setCandidatePool(optional(state, CANDIDATE_POOL, CandidatePool.class));
        context.setMacroRoutePlans(optional(state, MACRO_ROUTE_PLANS, MACRO_ROUTE_PLAN_LIST));
        context.setMacroRouteFacts(optional(state, MACRO_ROUTE_FACTS, MACRO_ROUTE_FACT_LIST));
        context.setRouteCriticResult(optional(state, ROUTE_CRITIC_RESULT, RouteCriticResult.class));
        context.setDaySkeletons(optional(state, DAY_SKELETONS, DAY_SKELETON_LIST));
        context.setWeatherForecast(optional(state, WEATHER_FORECAST, String.class));
        context.setHotelSearchResult(optional(state, HOTEL_SEARCH_RESULT, String.class));
        context.setDayContexts(optional(state, DAY_CONTEXTS, DAY_CONTEXT_LIST));
        context.setDayQueryPlans(optional(state, DAY_QUERY_PLANS, DAY_QUERY_PLAN_LIST));
        context.setFoodRecommendationsByDay(optional(state, FOOD_RECOMMENDATIONS_BY_DAY, FOOD_MAP));
        context.setRankedDayDataPackages(optional(state, RANKED_DAY_DATA_PACKAGES, DAY_DATA_PACKAGE_LIST));
        context.setDayValidationReports(optional(state, DAY_VALIDATION_REPORTS, DAY_VALIDATION_REPORT_LIST));
        context.setLockedDailyPlans(optional(state, LOCKED_DAILY_PLANS, DAILY_PLAN_LIST));
        context.setRecommendationContext(optional(state, RECOMMENDATION_CONTEXT, RecommendationContextDTO.class));
        context.setResult(optional(state, RESULT, TripGenerateResponse.class));
        context.setSingleDayGeneration(optional(state, SINGLE_DAY_GENERATION, Boolean.class));
        context.setTargetDayNo(optional(state, TARGET_DAY_NO, Integer.class));
        context.setPreviousDailyPlans(optional(state, PREVIOUS_DAILY_PLANS, DAILY_PLAN_LIST));
        context.setRevisionText(optional(state, REVISION_TEXT, String.class));
        return context;
    }

    static Map<String, Object> toState(GenerateWorkflowContext context) {
        return TripGraphStateCodec.patch(
                USER_ID, context.getUserId(),
                REQUEST, context.getRequest(),
                REQUIREMENT, context.getRequirement(),
                SELECTED_QUOTE, context.getSelectedQuote(),
                RENTAL_TRIP_CONTEXT, context.getRentalTripContext(),
                CITY_PROFILE, context.getCityProfile(),
                CANDIDATE_POOL, context.getCandidatePool(),
                MACRO_ROUTE_PLANS, context.getMacroRoutePlans(),
                MACRO_ROUTE_FACTS, context.getMacroRouteFacts(),
                ROUTE_CRITIC_RESULT, context.getRouteCriticResult(),
                DAY_SKELETONS, context.getDaySkeletons(),
                WEATHER_FORECAST, context.getWeatherForecast(),
                HOTEL_SEARCH_RESULT, context.getHotelSearchResult(),
                DAY_CONTEXTS, context.getDayContexts(),
                DAY_QUERY_PLANS, context.getDayQueryPlans(),
                FOOD_RECOMMENDATIONS_BY_DAY, context.getFoodRecommendationsByDay(),
                RANKED_DAY_DATA_PACKAGES, context.getRankedDayDataPackages(),
                DAY_VALIDATION_REPORTS, context.getDayValidationReports(),
                LOCKED_DAILY_PLANS, context.getLockedDailyPlans(),
                RECOMMENDATION_CONTEXT, context.getRecommendationContext(),
                RESULT, context.getResult(),
                SINGLE_DAY_GENERATION, context.getSingleDayGeneration(),
                TARGET_DAY_NO, context.getTargetDayNo(),
                PREVIOUS_DAILY_PLANS, context.getPreviousDailyPlans(),
                REVISION_TEXT, context.getRevisionText());
    }

    private static <T> T optional(OverAllState state, String key, Class<T> type) {
        return TripGraphStateCodec.optional(state, key, type).orElse(null);
    }

    private static <T> T optional(OverAllState state, String key, TypeReference<T> type) {
        return TripGraphStateCodec.optional(state, key, type).orElse(null);
    }
}
