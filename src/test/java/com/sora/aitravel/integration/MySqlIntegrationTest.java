package com.sora.aitravel.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

@EnabledIfSystemProperty(named = ExternalIntegrationTestSupport.ENABLE_PROPERTY, matches = "true")
class MySqlIntegrationTest extends ExternalIntegrationTestSupport {

    @Test
    void mysqlAcceptsCredentialsAndCanExecuteQuery() throws Exception {
        String database = requiredEnv("MYSQL_DATABASE");
        String url =
                "jdbc:mysql://%s:%d/%s?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true"
                        .formatted(requiredEnv("MYSQL_HOST"), requiredPort("MYSQL_PORT"), database);
        Properties properties = new Properties();
        properties.setProperty("user", requiredEnv("MYSQL_USERNAME"));
        properties.setProperty("password", requiredEnv("MYSQL_PASSWORD"));
        properties.setProperty("connectTimeout", "10000");
        properties.setProperty("socketTimeout", "10000");

        try (Connection connection = DriverManager.getConnection(url, properties);
                Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery("SELECT DATABASE(), 1")) {
            assertThat(result.next()).isTrue();
            assertThat(result.getString(1)).isEqualToIgnoringCase(database);
            assertThat(result.getInt(2)).isEqualTo(1);
            assertThat(connection.isValid(5)).isTrue();
        }
    }
}
