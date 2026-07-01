package com.sora.aitravel.common.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sora.aitravel.common.enums.ErrorCode;
import com.sora.aitravel.common.exception.BusinessException;
import java.io.IOException;
import java.io.InputStream;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

@Component
@RequiredArgsConstructor
public class JsonCodec {
    private final ObjectMapper objectMapper;

    public String write(Object value, String errorMessage) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new BusinessException(ErrorCode.AI_GENERATE_ERROR, errorMessage);
        }
    }

    public String writeNullable(Object value, String errorMessage) {
        if (value == null) {
            return null;
        }
        return write(value, errorMessage);
    }

    public <T> T read(String json, Class<T> type, String errorMessage) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new BusinessException(ErrorCode.AI_GENERATE_ERROR, errorMessage);
        }
    }

    public <T> T read(String json, TypeReference<T> type, String errorMessage) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new BusinessException(ErrorCode.AI_GENERATE_ERROR, errorMessage);
        }
    }

    public <T> T readNullable(String json, Class<T> type, String errorMessage) {
        if (json == null || json.isBlank() || "null".equals(json)) {
            return null;
        }
        return read(json, type, errorMessage);
    }

    public <T> T readNullable(String json, TypeReference<T> type, String errorMessage) {
        if (json == null || json.isBlank() || "null".equals(json)) {
            return null;
        }
        return read(json, type, errorMessage);
    }

    public <T> T readClasspath(String path, TypeReference<T> type, String errorMessage) {
        ClassPathResource resource = new ClassPathResource(path);
        try (InputStream inputStream = resource.getInputStream()) {
            String json =
                    StreamUtils.copyToString(inputStream, java.nio.charset.StandardCharsets.UTF_8);
            return read(json, type, errorMessage);
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.AI_GENERATE_ERROR, errorMessage);
        }
    }
}
