package com.sora.aitravel.integration;

import static org.assertj.core.api.Assertions.assertThat;

abstract class ExternalIntegrationTestSupport {

    static final String ENABLE_PROPERTY = "external.it";

    static String requiredEnv(String name) {
        String value = System.getenv(name);
        assertThat(value).as("环境变量 %s 必须存在且不能为空", name).isNotNull().isNotBlank();
        return value;
    }

    static String optionalEnv(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }

    static int requiredPort(String name) {
        String value = requiredEnv(name);
        try {
            int port = Integer.parseInt(value);
            assertThat(port).as("%s 必须是有效端口", name).isBetween(1, 65535);
            return port;
        } catch (NumberFormatException exception) {
            throw new AssertionError(name + " 必须是数字端口", exception);
        }
    }

    static String withoutTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
