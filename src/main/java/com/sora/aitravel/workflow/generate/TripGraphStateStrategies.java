package com.sora.aitravel.workflow.generate;

import com.alibaba.cloud.ai.graph.KeyStrategyFactory;
import com.alibaba.cloud.ai.graph.KeyStrategyFactoryBuilder;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;

/** Centralized state merge strategy registration for trip generation graphs. */
public final class TripGraphStateStrategies {
    private TripGraphStateStrategies() {}

    public static KeyStrategyFactory build() {
        KeyStrategyFactoryBuilder builder = new KeyStrategyFactoryBuilder();
        for (String key : replaceKeys()) {
            builder.addStrategy(key, new ReplaceStrategy());
        }
        return builder.build();
    }

    private static String[] replaceKeys() {
        return new String[] {
            TripGraphStateKeys.USER_ID,
            TripGraphStateKeys.REQUEST,
            TripGraphStateKeys.REQUIREMENT,
            TripGraphStateKeys.SELECTED_QUOTE,
            TripGraphStateKeys.RENTAL_TRIP_CONTEXT,
            TripGraphStateKeys.CITY_PROFILE,
            TripGraphStateKeys.CANDIDATE_POOL,
            TripGraphStateKeys.DAY_SKELETONS,
            TripGraphStateKeys.WEATHER_FORECAST,
            TripGraphStateKeys.HOTEL_SEARCH_RESULT,
            TripGraphStateKeys.DAY_CONTEXTS,
            TripGraphStateKeys.DAY_QUERY_PLANS,
            TripGraphStateKeys.RANKED_DAY_DATA_PACKAGES,
            TripGraphStateKeys.DAY_VALIDATION_REPORTS,
            TripGraphStateKeys.LOCKED_DAILY_PLANS,
            TripGraphStateKeys.RECOMMENDATION_CONTEXT,
            TripGraphStateKeys.RESULT,
            TripGraphStateKeys.SINGLE_DAY_GENERATION,
            TripGraphStateKeys.TARGET_DAY_NO,
            TripGraphStateKeys.PREVIOUS_DAILY_PLANS,
            TripGraphStateKeys.REVISION_TEXT
        };
    }
}
