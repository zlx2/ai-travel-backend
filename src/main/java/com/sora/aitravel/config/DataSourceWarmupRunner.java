package com.sora.aitravel.config;

import java.sql.Connection;
import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** 启动后预热数据库连接池，避免首次业务请求支付建连成本。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataSourceWarmupRunner implements ApplicationRunner {

    private final DataSource dataSource;

    @Override
    public void run(ApplicationArguments args) {
        long start = System.currentTimeMillis();
        try (Connection ignored = dataSource.getConnection()) {
            log.info("数据库连接池预热完成，elapsedMs={}", System.currentTimeMillis() - start);
        } catch (Exception exception) {
            log.warn("数据库连接池预热失败，首次业务请求可能变慢，reason={}", exception.getMessage());
        }
    }
}
