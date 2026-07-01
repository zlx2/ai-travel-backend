package com.sora.aitravel.workflow.generate.state;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Typed access helpers for Spring AI Alibaba Graph state values. */
public final class TripGraphStateCodec {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private TripGraphStateCodec() {}

    public static <T> T required(OverAllState state, String key, Class<T> type) {
        return optional(state, key, type)
                .orElseThrow(() -> new IllegalStateException("Trip graph state is missing " + key));
    }

    public static <T> Optional<T> optional(OverAllState state, String key, Class<T> type) {
        Optional<Object> value = state.value(key);
        if (value.isEmpty() || value.get() == null) {
            return Optional.empty();
        }
        return Optional.of(convert(value.get(), type));
    }

    public static <T> T required(OverAllState state, String key, TypeReference<T> type) {
        return optional(state, key, type)
                .orElseThrow(() -> new IllegalStateException("Trip graph state is missing " + key));
    }

    public static <T> List<T> optionalList(OverAllState state, String key, Class<T> itemType) {
        Optional<Object> value = state.value(key);
        if (value.isEmpty() || value.get() == null) {
            return List.of();
        }
        return OBJECT_MAPPER.convertValue(
                value.get(),
                OBJECT_MAPPER.getTypeFactory().constructCollectionType(List.class, itemType));
    }

    public static <T> Optional<T> optional(OverAllState state, String key, TypeReference<T> type) {
        Optional<Object> value = state.value(key);
        if (value.isEmpty() || value.get() == null) {
            return Optional.empty();
        }
        return Optional.of(convert(value.get(), type));
    }

    public static Map<String, Object> patch(Object... keyValues) {
        if (keyValues.length % 2 != 0) {
            throw new IllegalArgumentException("State patch requires key/value pairs");
        }
        Map<String, Object> patch = new LinkedHashMap<>();
        for (int index = 0; index < keyValues.length; index += 2) {
            patch.put((String) keyValues[index], keyValues[index + 1]);
        }
        return patch;
    }

    private static <T> T convert(Object value, Class<T> type) {
        if (type.isInstance(value)) {
            return type.cast(value);
        }
        return OBJECT_MAPPER.convertValue(value, type);
    }

    private static <T> T convert(Object value, TypeReference<T> type) {
        return OBJECT_MAPPER.convertValue(value, type);
    }
}
