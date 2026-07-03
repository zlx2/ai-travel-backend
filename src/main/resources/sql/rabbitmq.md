# RabbitMQ 配置与说明

## 连接配置

| 参数 | 值 |
|------|------|
| Host | `${RABBITMQ_HOST}`（默认 `127.0.0.1`） |
| Port | `${RABBITMQ_PORT}`（默认 `5672`） |
| Username | `${RABBITMQ_USERNAME}` |
| Password | `${RABBITMQ_PASSWORD}` |

## 队列与交换机

配置在 `RabbitMqConfig.java`。

| 项目 | 值 |
|------|------|
| Queue | `trip.day.generate.queue` |
| Exchange | `trip.day.generate.exchange` |
| Routing Key | `trip.day.generate` |
| Listener | `consumeTripDayGenerate(TripDayGenerateMessage)` |

消息类型：`TripDayGenerateMessage`（`sessionId`, `dayNo`, `requestMode`, `forceRegenerate`）

## 工作流

```
用户提交需求 → AiTripController
  → ai_trip_generation_session 创建 (PREPARING)
  → 消息发到 trip.day.generate.exchange
  → trip.day.generate.queue 消费
  → AiTripDayGenerateOrchestrator.generateDay()
  → 逐日生成，结果写入 ai_trip_day_generation
  → 所有天完成后 → session.status = COMPLETED
```

同时支持**多版本重试**：同一天的多次生成保存为不同 `generation_version`。
