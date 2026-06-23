# PlanGo 后端

AI 智能旅行规划平台后端。Spring Boot 3.5 + Java 17，包名 `com.sora.aitravel`，接口前缀 `/api`。

## 技术栈

Spring Boot 3.5、MyBatis-Plus、Sa-Token、Spring AI 1.1.8、Spring AI Alibaba Agent Framework、DeepSeek、DashScope Embedding、MySQL、Redis、RabbitMQ、腾讯云 COS。

## 已实现

- 认证体系：邮箱验证码注册/登录/退出、当前用户、资料修改。
- 行程模块：创建行程、AI 分析目的地偏好、AI 生成行程计划。
- 游记模块：发布/编辑/删除游记、点赞、收藏、评论。
- 目的地与标签管理。
- 文件上传（腾讯云 COS）。
- 管理员操作日志。
- 通用响应封装、全局异常处理。

## 租车模块 (进行中)

**数据层已完成：** ai_travel 库包含 5 张租车表（rental_vehicle_group / rental_vehicle_model / rental_price_template / rental_pickup_poi / rental_order），含真实车型、价格模板和高德 POI 数据。Flask 演示页面可独立展示数据（`/rental/demo`）。

**后端代码：** 仅实现了 `RentalStoreResolveNode`（租车服务点解析），尚无 Controller 和完整 API。

## 代码分层

```
com.sora.aitravel
├── controller/    # 15 个 Controller
├── service/       # 业务逻辑
├── mapper/        # MyBatis 数据访问
├── entity/        # 数据库实体
├── dto/           # 请求/响应/模型 DTO
├── workflow/      # AI 工作流编排 (analyze/generate/trip/rental)
├── prompt/        # AI Prompt 模板
├── config/        # Spring 配置
├── tools/         # AI Tool 定义
└── common/        # 通用枚举/异常/响应
```

## 数据库

ai_travel 库 18 张表：admin_operation_log / ai_call_log / ai_conversation / destination / file_resource / note / note_comment / note_favorite / note_like / note_tag / sys_user / tag / trip / rental_vehicle_group / rental_vehicle_model / rental_price_template / rental_pickup_poi / rental_order。

结构 + 示例数据文件：`ai_travel_schema_sample.sql`

## 环境配置

复制 `.env.example` 为 `.env`，填写各项配置。不要提交 `.env`。

```bash
mvn clean package              # 编译
mvn spotless:apply             # 格式化
mvn -Dexternal.it=true test    # 集成测试
docker compose up -d --build   # 容器启动
```

## 关键约定

- 认证头：`Authorization: Bearer <token>`
- AI Analyze 状态：READY / NEED_MORE_INFO / CONFLICT / NEED_DESTINATION_CHOICE
- 行程生成要求 departure + destination + days(1-7)
- 游记状态：草稿 / 已发布 / 已删除
