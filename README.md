# PlanGo 后端

AI 智能旅行规划平台后端，Spring Boot 骨架，包名 `com.sora.aitravel`，接口前缀 `/api`。

## 当前范围

- Maven、通用响应、全局异常、配置层。
- 18 张表的 Entity、Mapper、SQL（ai_travel 库：13 张业务表 + 5 张租车表）。
- 一期 DTO、Controller 路由和 Service 契约。
- Analyze、Generate、TRIP Chat 顺序 Workflow 及 Prompt。
- 最终版需求：邮箱验证码、注册、登录、退出、当前用户及资料修改。
- 接入 Spring AI 1.1.8、Spring AI Alibaba 1.1.2.3 Agent Framework、DeepSeek 与 DashScope Embedding。
- Dockerfile 与 Docker Compose。
- 除认证与用户资料外，其余业务实现仍以 TODO/501 响应保留。

## 租车模块 (car_rental)

已完整实现模拟租车数据层：

- **rental_vehicle_group** — 7 个车型组（经济型/舒适型/中型/SUV/MPV/新能源/豪华）。
- **rental_vehicle_model** — 95 款真实车型，覆盖 25 个品牌，含指导价、能源类型等。
- **rental_price_template** — 25 个城市 × 7 车型组 = 175 条价格模板，含工作日/周末租费、服务费、整备费、押金、异地还车规则。
- **rental_pickup_poi** — 555 个取还车 POI（高德真实数据），覆盖机场/火车站/商圈/酒店/景区等。
- **rental_order** — 订单表，含完整的费用快照字段（租费/服务费/整备费/异地还车费/押金/送车上门费）。

价格结构：车辆租赁费 + 基础服务费（按天）+ 车辆整备费（按单）+ 异地还车费（动态计算，MVP 全额优惠）。押金不计入总价，芝麻分免押。

## 代码分层

```text
com.sora.aitravel
├── controller
├── service
│   └── impl
├── mapper
├── entity
├── dto
│   ├── request
│   ├── response
│   └── model
├── workflow
├── prompt
├── config
└── common
```

## 数据库

ai_travel 库共 18 张表：

| 模块 | 表名 |
|------|------|
| 管理 | admin_operation_log |
| AI | ai_call_log, ai_conversation |
| 目的地 | destination |
| 文件 | file_resource |
| 游记 | note, note_comment, note_favorite, note_like, note_tag |
| 用户 | sys_user |
| 标签 | tag |
| 行程 | trip |
| 租车 | rental_vehicle_group, rental_vehicle_model, rental_price_template, rental_pickup_poi, rental_order |

数据库结构 + 示例数据文件：`ai_travel_schema_sample.sql`

## 环境配置

复制 `.env.example` 为 `.env`，填写 MySQL、Redis、RabbitMQ、SMTP、COS、DeepSeek 配置。不要提交 `.env`。

开发环境可通过 `EMAIL_MOCK_CODE` 设置固定验证码；生产环境不要配置。

```bash
mvn clean package       # 编译
mvn spotless:apply      # 格式化
mvn "-Dexternal.it=true" test  # 集成测试
docker compose up -d --build   # 容器启动
```

## 重要约束

- Sa-Token 请求头：`Authorization: Bearer <token>`
- AI Analyze 状态：`READY / NEED_MORE_INFO / CONFLICT / NEED_DESTINATION_CHOICE`
- Generate 必须具备 `departure / destination / days`，days 为 1-7
- AI Chat 一期仅支持 `TRIP`
- 游记状态：草稿、已发布、已删除
