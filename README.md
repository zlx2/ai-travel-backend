# PlanGo 智能旅行规划平台 · 后端

基于 Spring Boot 3 + Spring AI + MyBatis-Plus 的 AI 旅行规划引擎，提供从需求分析到完整行程生成的端到端服务。

## 技术栈

| 能力 | 选型 |
|------|------|
| 框架 | Spring Boot 3.5.15 / Java 21 / Maven |
| ORM | MyBatis-Plus 3.5.16 + MySQL 8 |
| AI | Spring AI 1.1.8 + DeepSeek ChatModel + DashScope Embedding |
| 工作流引擎 | Spring AI Alibaba Agent Framework + AlibabaGraphWorkflow |
| 认证 | Sa-Token 1.45.0 + Redis 持久化 |
| 缓存/消息 | Redis + RabbitMQ |
| 存储 | 腾讯云 COS（文件/图片） |
| 支付 | 支付宝沙箱 SDK |
| 地图 | 高德 REST API（POI 搜索/路线规划/地理编码） |
| 代码规范 | Lombok + Spotless (google-java-format) |

## 系统架构

```
用户请求 → Nginx(80) → API(10001) → Controller
                                        │
                    ┌───────────────────┼───────────────────┐
                    ▼                   ▼                   ▼
               Auth(Sa-Token)     AI 工作流引擎        业务服务
                                    │
                    ┌───────────────┴───────────────┐
                    ▼                               ▼
            AI 需求分析                          行程生成
        TripAnalyzeWorkflow             TripGenerateWorkflow
                    │                               │
        ┌───────────┼───────────┐       ┌───────────┼───────────┐
        ▼           ▼           ▼       ▼           ▼           ▼
   信息抽取    约束标准化   冲突检查   候选数据    逐日生成    路线编排
                                         │
                                    AI Gateway
                                    (DeepSeek ChatClient)
```

## API 路由一览

所有接口挂载在 `/api` 前缀下。

### 用户与认证 (`/auth`, `/users`)

- `POST /auth/email-code` — 发送邮箱验证码
- `POST /auth/register` — 注册（邮箱 + 验证码）
- `POST /auth/login` — 登录（邮箱/用户名 + 密码）
- `POST /auth/logout` — 退出登录
- `POST /auth/password-reset` — 重置密码
- `GET /users/me` — 获取当前用户信息
- `PUT /users/me` — 更新个人资料
- `GET /users/me/stats` — 用户统计（行程数/游记数/点赞数）
- `POST /users/me/email-code` — 发送换绑邮箱验证码
- `PUT /users/me/email` — 换绑邮箱

### AI 行程规划 (`/ai/trips`)

- `POST /ai/trips/analyze` — AI 需求分析：接收自然语言输入，提取目的地、天数、预算、节奏等结构化需求
- `POST /ai/trips/generate` — 完整行程生成（同步）：基于需求生成完整行程
- `POST /ai/trips/generate/session` — 创建生成会话
- `POST /ai/trips/generate/session/{sessionId}/days/{dayNo}` — 单日行程生成（支持异步轮询）
- `POST /ai/trips/generate/stream` — SSE 流式行程生成（实时进度推送）

### 行程管理 (`/trips`)

- `POST /trips` — 保存行程
- `GET /trips/my` — 我的行程列表（分页）
- `GET /trips/{id}` — 行程详情
- `PUT /trips/{id}` — 更新行程
- `DELETE /trips/{id}` — 删除行程

### 游记系统 (`/notes`, `/notes/{id}/...`)

- `GET /notes` — 游记列表（分页 + 关键词/标签筛选）
- `GET /notes/my` — 我的游记
- `POST /notes` — 创建游记
- `GET /notes/{id}` — 游记详情
- `PUT /notes/{id}` — 更新游记
- `DELETE /notes/{id}` — 删除游记
- `POST /notes/{id}/like` / `DELETE /notes/{id}/like` — 点赞/取消
- `POST /notes/{id}/favorite` / `DELETE /notes/{id}/favorite` — 收藏/取消
- `GET /notes/{id}/comments` / `POST /notes/{id}/comments` — 评论列表/发表评论
- `DELETE /comments/{id}` — 删除评论

### 租车系统 (`/rental`)

- `POST /rental/context/preview` — 租车场景预览（目的地/到达方式分析）
- `POST /rental/quotes/preview` — 报价预览
- `GET /rental/quotes/latest-ordered` — 最近下单的报价
- `POST /rental/orders` — 创建租车订单
- `POST /rental/orders/{id}/pay` — 模拟支付
- `POST /rental/orders/{id}/alipay/page-pay` — 支付宝沙箱页面支付
- `GET /rental/orders/my` — 我的租车订单
- `GET /rental/orders/{id}` — 订单详情
- `POST /rental/orders/{id}/cancel` — 取消订单

### 通用能力

- `GET /home` — 首页聚合（热门目的地/游记/标签）
- `GET /tags` — 标签列表
- `POST /files/upload` — 文件上传（头像/图片）

## AI 工作流详解

### 需求分析工作流 (`workflow/analyze/`)

将用户自然语言输入转化为结构化旅行需求，节点链：

```
UserRawInput → InputPreprocess → RequirementStandardize
    → InfoExtract → CompletenessCheck → ConflictCheck
    → AnalyzeResultMerge → 输出结构化 Requirement
```

- 基于 DeepSeek ChatModel 做语义理解和信息抽取
- 自动检测需求矛盾（如时间不够覆盖远郊景点）
- 补充缺失信息（预算、节奏偏好等）

### 行程生成工作流 (`workflow/generate/`)

两级工作流编排：

**1. TripPrepareWorkflow** — 行程准备阶段
- `RequirementPrepareNode` — 需求预处理
- `DestinationPrepareNode` — 目的地决策（高德 POI/天气/酒店/美食数据采集）
- `ExternalContextPrepareNode` — 外部上下文（季节/天气/交通）
- `MacroRoutePrepareNode` — 宏观路线规划（片区排序、距离校验）
- `PrepareFinalizeNode` — 准备阶段收尾

**2. TripDayGenerateWorkflow** — 逐日生成（支持异步/流式）
- `DayCandidatePrepareNode` — 候选 POI 数据准备
- `DayInputPrepareNode` — 单日输入组装
- `DayPlanGenerateNode` — AI 逐日计划生成（含景点/路线/餐饮/预算）
- `DayPlanValidateNode` — 计划合规性校验
- `DayPlanFinalizeNode` — 单日收尾

**数据约束层：**
- 基于离线知识库（travel_city / travel_area / travel_spot）限定白名单
- 严格禁止 AI 捏造景点或坐标
- 距离合理性校验（远郊与市中心组合检测）
- 体力强度/推荐时长/最佳时间等语义字段参与规划

## 数据库设计

核心业务表（20 张）：

| 领域 | 表 |
|------|----|
| 用户 | `sys_user` |
| 旅游知识库 | `travel_city` / `travel_area` / `travel_spot` |
| AI 生成 | `ai_trip_generation_session` / `ai_trip_day_generation` |
| 行程 | `trip` + 相关表 |
| 游记 | `note` / `note_comment` / `note_like` / `note_favorite` / `note_tag` |
| 标签 | `tag` |
| 目的地 | `destination` |
| 文件 | `file_resource` |
| 租车 | `rental_order` / `rental_pickup_poi` / `rental_price_template` / `rental_vehicle_group` / `rental_vehicle_model` |

数据初始化脚本：`src/main/resources/sql/schema.sql` + `init-data.sql`

## 外部依赖

| 服务 | 用途 | 必须？ |
|------|------|--------|
| MySQL | 业务数据库 | 是 |
| Redis | 缓存 + Sa-Token 持久化 | 是 |
| RabbitMQ | 异步消息（行程生成） | 是 |
| DeepSeek API | AI 对话/推理 | 是（可 Mock） |
| DashScope API | 文本向量化 | 知识库检索需要 |
| 高德 REST API | POI 搜索/路线/地理编码 | 是 |
| 腾讯云 COS | 文件/图片存储 | 选 |
| 支付宝沙箱 | 支付演示 | 选 |

## 本地运行

```bash
# 1. 环境准备
cp .env.example .env  # 填写 MySQL/Redis/RabbitMQ 连接信息
                       # DeepSeek Key / 高德 Key 等

# 2. 初始化数据库
mysql -u root -p < src/main/resources/sql/schema.sql
mysql -u root -p < src/main/resources/sql/init-data.sql

# 3. 编译启动
mvn clean package -DskipTests
java -jar target/plango-backend-0.0.1-SNAPSHOT.jar

# 默认端口 8080，API 前缀 /api
# 环境变量 SERVER_PORT 可覆盖端口
```
