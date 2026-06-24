# PlanGo 后端

一期 Spring Boot 后端骨架，基础包名为 `com.sora.aitravel`，接口前缀为 `/api`。

## 今日任务（6月24日）

| 任务 | 状态 |
|------|------|
| 修复美食模块自定义规则 bug（意图识别不准确） | 待做 |
| 把美食推荐接入 travel generate 工作流（替换 MockFoodRecommendNode） | 待做 |
| 景点工具接入工作流（替换 MockScenicSpotRecommendNode） | 待做 |
| 联调测试：food 工具测试 + 工作流测试 | 待做 |

<details>
<summary>昨日完成 & 遇到的问题</summary>

### 昨日完成（6月23日）

| 事项 | 说明 |
|------|------|
| 美食推荐模块 | 接入高德餐饮 POI，支持附近美食/地点附近/城市特色美食 |
| 查询意图识别 | 自定义规则 + LLM 兜底，识别 NEAR_CURRENT / NEAR_ADDRESS / CITY_KEYWORD |
| 推荐理由生成 | LLM 优先，失败模板兜底，只拼高德真实字段 |
| 景点工具注册 | ScenicTool 基于高德 POI 返回景点名称列表 |
| 测试 | FoodToolTest 5 个用例覆盖三种查询场景 + 异常场景 |

### 昨日遇到的问题

| 问题 | 解决方案 |
|------|----------|
| 用户输入口语化，全靠 LLM → 慢+贵 | 自定义规则优先，匹配不到才走 LLM |
| 高德 business 字段不统一 | valueFromBusinessOrPoi 双层取值 + isValidFoodPoi 过滤 |
| Open-Meteo gzip 压缩 Hutool 解压失败 | Accept-Encoding: identity 禁用压缩 |
| DeepSeek 日期认知偏差（以为 2025） | 系统提示词注入 LocalDate.now() |
| 数据库字段初期遗漏（城市/订单等） | 后续补充，租车模块 5 张表已覆盖 |

</details>

## 当前范围

- 已生成 Maven、通用响应、全局异常、配置层。
- 已生成 13 张表的 Entity、Mapper、SQL。
- 已生成一期 DTO、Controller 路由和 Service 契约。
- 已生成 Analyze、Generate、TRIP Chat 顺序 Workflow 及 Prompt。
- 已按最终版需求实现邮箱验证码、注册、登录、退出、当前用户及资料修改。
- 已接入 Spring AI 1.1.8、Spring AI Alibaba 1.1.2.3 Agent Framework、DeepSeek 与 DashScope Embedding。
- 已生成 Dockerfile 与 Docker Compose。
- 除认证与用户资料外，其余业务实现仍以 TODO/501 响应保留，不返回伪造成功数据。

## 代码分层

项目采用横向分层，不按业务模块建立顶层包：

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

## 环境配置

复制 `.env.example` 为 `.env`，自行填写 MySQL、Redis、RabbitMQ、SMTP、COS、DeepSeek 配置。不要提交 `.env`。

初始化 SQL 不创建固定密码管理员。首次部署后应通过单独的安全初始化流程创建管理员账号，不得在公开仓库保存默认密码。

开发环境可通过 `EMAIL_MOCK_CODE` 显式设置固定的 6 位验证码；未设置时验证码通过 SMTP 发送。生产和演示环境不要配置固定验证码。

本地编译：

```bash
mvn clean package
```

格式化 Java 代码：

```bash
mvn spotless:apply
```

真实外部配置测试默认不会执行，以免产生邮件、云服务或模型调用。配置完成后显式运行：

```bash
mvn "-Dexternal.it=true" test
```

测试会验证 MySQL、Redis、Redis Stack、RabbitMQ、SMTP、COS、DeepSeek 和 DashScope Embedding。
SMTP 测试只认证不发信；COS 测试只读 Bucket ACL；Redis/RabbitMQ 使用并清理临时数据。

`mvn verify` 会自动检查格式，格式不符合规则时构建失败。

容器启动：

```bash
docker compose up -d --build
```

## 重要约束

- Sa-Token 请求头：`Authorization: Bearer <token>`。
- AI Analyze 状态只有 `READY / NEED_MORE_INFO / CONFLICT / NEED_DESTINATION_CHOICE`。
- Generate 必须具备 `departure / destination / days`，且 `days` 为 1-7。
- AI Chat 一期仅支持 `TRIP`，不修改行程、不保存完整聊天历史。
- 游记状态只有草稿、已发布、已删除，不包含审核流。
