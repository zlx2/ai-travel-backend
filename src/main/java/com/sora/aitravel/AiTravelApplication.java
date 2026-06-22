package com.sora.aitravel;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * PlanGo 后端启动入口。
 *
 * <p>Spring Boot 3.5.15 主启动类，负责：
 * <ul>
 *   <li>自动装配所有 Spring Bean（Controller / Service / Mapper / Config）</li>
 *   <li>初始化 MyBatis-Plus、Sa-Token、RabbitMQ、Redis 等基础设施</li>
 *   <li>加载 application.yml 配置和环境变量</li>
 * </ul>
 *
 * <p>启动方式：
 * <pre>
 * cd ai-travel-backend
 * source .env &amp;&amp; mvn spring-boot:run
 * </pre>
 *
 * @author tsumugi
 */
@SpringBootApplication
public class AiTravelApplication {

    /**
     * 服务入口方法。
     *
     * @param args 命令行参数（可通过 --server.port=8080 覆盖端口等）
     */
    public static void main(String[] args) {
        SpringApplication.run(AiTravelApplication.class, args);
    }
}
