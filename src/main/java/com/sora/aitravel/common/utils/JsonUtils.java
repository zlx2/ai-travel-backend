package com.sora.aitravel.common.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sora.aitravel.common.enums.ErrorCode;
import com.sora.aitravel.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * JSON 序列化/反序列化工具类。
 *
 * <p>封装 Jackson 的 ObjectMapper，提供将对象转为 JSON 字符串以及将 JSON 字符串转为对象的便捷方法。 转换失败时抛出 {@link
 * BusinessException} 而非原始受检异常。
 */
@Component
@RequiredArgsConstructor
public class JsonUtils {
    private final ObjectMapper objectMapper;

    /**
     * 将 Java 对象序列化为 JSON 字符串。
     *
     * @param value 要序列化的对象
     * @return JSON 字符串
     * @throws BusinessException 如果序列化失败
     */
    public String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "JSON 序列化失败");
        }
    }

    /**
     * 将 JSON 字符串反序列化为指定类型的 Java 对象。
     *
     * @param json JSON 字符串
     * @param type 目标类型
     * @param <T> 目标类型泛型
     * @return 反序列化后的对象
     * @throws BusinessException 如果反序列化失败
     */
    public <T> T fromJson(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new BusinessException(ErrorCode.AI_RESPONSE_FORMAT_ERROR);
        }
    }
}
