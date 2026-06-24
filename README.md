# PlanGo 后端

PlanGo 智能旅行规划平台后端，当前处于课程项目/联调开发阶段。项目已经搭起 Spring Boot API、AI 工作流、高德工具、租车流程等骨架，其中一部分接口可真实调用，一部分仍返回 501 占位响应。

## 当前状态

- 主分支：`dev`
- Java 版本：`21`
- 默认端口：`8080`
- API 前缀：`/api`
- 数据库脚本：`src/main/resources/sql/schema.sql`、`src/main/resources/sql/init-data.sql`
- 真实运行依赖：MySQL、Redis、RabbitMQ、DeepSeek Key；部分能力还需要 DashScope、腾讯云 COS、高德 Key

## 技术栈

- Spring Boot `3.5.15`
- Java `21`
- Maven
- MyBatis-Plus `3.5.16`
- MySQL Connector/J `9.7.0`
- Redis、RabbitMQ、Spring Mail
- Sa-Token `1.45.0`
- Spring AI `1.1.8`
- Spring AI Alibaba `1.1.2.3`
- DeepSeek ChatModel / ChatClient
- DashScope Embedding
- 腾讯云 COS
- 高德 REST API
- Lombok
- Spotless + google-java-format AOSP

## 目录说明

```text
src/main/java/com/sora/aitravel
├── ai/             # 项目级 AI 调用门面，当前用 ChatClient 封装通用调用
├── client/amap/    # 高德 HTTP 客户端
├── common/         # 统一响应、异常、枚举、工具类
├── config/         # Spring、Sa-Token、Redis、COS、高德等配置
├── controller/     # REST API 控制器
├── dto/            # 请求、响应、模型 DTO
├── entity/         # MyBatis-Plus 实体
├── mapper/         # MyBatis-Plus Mapper
├── prompt/         # Prompt 模板加载
├── service/        # 业务服务接口与实现
├── tools/          # Spring AI Tool 定义
└── workflow/       # AI 分析、生成、聊天、租车等工作流
```

## 已接入/开发中的能力

### 已有真实实现或可联调

- `/api/debug/connect`：前后端连通性测试。
- `/api/auth/*`：注册、登录、邮箱验证码、退出登录。
- `/api/users/me`：当前用户资料读取与更新。
- `/api/home`：首页聚合数据，读取热门目的地、热门游记、标签。
- `/api/ai/trips/analyze`：AI 需求分析工作流，使用项目级 `AiGateway` 和 Spring AI `ChatClient` 调用 DeepSeek。
- `/api/ai/trips/generate`：行程生成工作流已串起需求、天气、酒店、美食、候选数据、逐日生成等节点，但仍含模拟/兜底数据。
- `/api/rental/*`：租车报价预览、订单创建、支付演示流程。
- 高德工具：POI、路线、地理编码、静态地图等工具类和服务已存在，部分生成节点仍在逐步接入。

### 仍是骨架/占位

以下模块中仍有接口返回 `501 接口骨架已生成，业务逻辑待实现`：

- 管理后台 `AdminController`
- 目的地列表/热门 `DestinationController`
- 标签 `TagController`
- 游记、评论、点赞收藏相关接口
- 行程 CRUD `TripController`
- 文件上传 `FileController`
- AI 聊天 `/api/ai/chat`

### AI 工作流现状

- `workflow/analyze`：真实调用 DeepSeek 做需求抽取，并做完整性检查、冲突检查和结果合并。
- `workflow/generate`：已经有天气、酒店、美食、高德候选数据、逐日计划生成等节点；部分节点仍以 `SIMULATED_AMAP`、`MOCK` 或规则生成作为兜底。
- `workflow/chat`：结构已搭好，模型调用节点仍是 TODO。
- `ai/AiGateway`：薄封装 `ChatClient`，统一文本/JSON 对象调用、场景日志、空响应与异常处理。业务 Prompt 仍保留在各工作流节点。

## 环境变量

复制 `.env.example` 后填写本地值。不要提交真实密码和密钥。

```bash
cp .env.example .env
```

核心变量：

```text
MYSQL_HOST / MYSQL_PORT / MYSQL_DATABASE / MYSQL_USERNAME / MYSQL_PASSWORD
REDIS_HOST / REDIS_PORT / REDIS_USERNAME / REDIS_PASSWORD / REDIS_DATABASE
RABBITMQ_HOST / RABBITMQ_PORT / RABBITMQ_USERNAME / RABBITMQ_PASSWORD
MAIL_HOST / MAIL_PORT / MAIL_USERNAME / MAIL_PASSWORD
DEEPSEEK_API_KEY / DEEPSEEK_BASE_URL / DEEPSEEK_MODEL
DASHSCOPE_API_KEY / DASHSCOPE_BASE_URL
COS_SECRET_ID / COS_SECRET_KEY / COS_BUCKET / COS_REGION / COS_DOMAIN
AMAP_API_KEY / AMAP_BASE_URL / AMAP_TIMEOUT
AI_MOCK_ENABLED
```

`application.yml` 直接从环境变量读取这些值。本地运行前请确保当前终端或 IDE Run Configuration 能读取到它们。

## 本地运行

```bash
mvn clean package
mvn spring-boot:run
```

或：

```bash
java -jar target/plango-backend-0.0.1-SNAPSHOT.jar
```

如果 8080 端口占用：

```bash
set SERVER_PORT=8081
mvn spring-boot:run
```

PowerShell：

```powershell
$env:SERVER_PORT="8081"
mvn spring-boot:run
```

## 常用检查

```bash
mvn -q -DskipTests test-compile
mvn test
mvn spotless:apply
mvn -B spotless:check
```

说明：`spotless:check` 会检查全量 Java 格式；如果遇到历史换行或格式问题，先确认是否属于当前改动范围。

## 数据库

初始化脚本位于：

- `src/main/resources/sql/schema.sql`
- `src/main/resources/sql/init-data.sql`

主要表包括：

- `sys_user`
- `destination`
- `tag`
- `trip`
- `note`
- `note_comment`
- `rental_*`
- `ai_conversation`
- `ai_call_log`

## 与前端联调

前端开发环境默认通过 Vite 代理访问后端：

```text
浏览器 -> /api -> Vite dev server -> http://127.0.0.1:8080
```

后端启动后，前端 `.env.development` 中保持：

```text
VITE_API_BASE_URL=/api
VITE_BACKEND_TARGET=http://127.0.0.1:8080
```

## 注意事项

- 不要把真实 `.env`、密钥、数据库密码提交到仓库。
- 看到 501 不一定是系统异常，很多接口仍处于骨架阶段。
- AI 行程生成当前还在“真实工具 + 模拟兜底”混合阶段，不要把所有地点/费用当成生产数据。
- 高德 JS 地图测试页在前端；后端高德配置主要服务 REST API 和工具节点。
