package com.sora.aitravel.workflow.generate.state;

/** State keys used by trip generation Spring AI Alibaba Graph workflows. */
public final class TripGraphStateKeys {
    private TripGraphStateKeys() {}

    public static final String USER_ID = "userId";
    public static final String REQUEST = "request";
    public static final String REQUIREMENT = "requirement";
    public static final String SELECTED_QUOTE = "selectedQuote";
    public static final String RENTAL_TRIP_CONTEXT = "rentalTripContext";
    public static final String CITY_PROFILE = "cityProfile";
    public static final String CANDIDATE_POOL = "candidatePool";
    public static final String MACRO_ROUTE_PLANS = "macroRoutePlans";
    public static final String MACRO_ROUTE_FACTS = "macroRouteFacts";
    public static final String ROUTE_CRITIC_RESULT = "routeCriticResult";
    public static final String DAY_SKELETONS = "daySkeletons";
    public static final String WEATHER_FORECAST = "weatherForecast";
    public static final String HOTEL_SEARCH_RESULT = "hotelSearchResult";
    public static final String DAY_CONTEXTS = "dayContexts";
    public static final String DAY_QUERY_PLANS = "dayQueryPlans";
    public static final String RANKED_DAY_DATA_PACKAGES = "rankedDayDataPackages";
    public static final String DAY_VALIDATION_REPORTS = "dayValidationReports";
    public static final String LOCKED_DAILY_PLANS = "lockedDailyPlans";
    public static final String RECOMMENDATION_CONTEXT = "recommendationContext";
    public static final String RESULT = "result";
    public static final String SINGLE_DAY_GENERATION = "singleDayGeneration";
    public static final String TARGET_DAY_NO = "targetDayNo";
    public static final String PREVIOUS_DAILY_PLANS = "previousDailyPlans";
    public static final String REVISION_TEXT = "revisionText";
}
