# PlanGo 后端

Spring Boot 3.5 + Java 17，包名 `com.sora.aitravel`，接口前缀 `/api`。

## 今日任务（6月24日）

| 任务 | 状态 |
|------|------|
| 修复美食模块自定义规则 bug（意图识别不准确） | 进行中 |
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

## 技术栈

Spring Boot 3.5 / MyBatis-Plus / Sa-Token / Spring AI 1.1.8 / Spring AI Alibaba Agent Framework / DeepSeek / DashScope Embedding / MySQL / Redis / RabbitMQ / 腾讯云 COS

## 已实现的 API

| 模块 | 接口 | 状态 |
|------|------|------|
| 认证 | 发送邮箱验证码、注册、登录、退出 | 完成 |
| 用户 | 获取当前用户、修改资料 | 完成 |
| 行程 | 创建/列表/详情/修改/删除 | 完成 |
| AI 分析 | POST /api/ai/trips/analyze | 完成（工作流编排） |
| AI 生成 | POST /api/ai/trips/generate | 完成（工作流编排） |
| AI 对话 | POST /api/ai/chat | 未实现（返回 501） |
| 游记 | 列表/创建/详情/修改/删除 | 完成 |
| 游记互动 | 点赞/收藏/评论 | 完成 |
| 目的地 | 列表 | 完成 |
| 标签 | 列表 | 完成 |
| 文件 | 上传（COS） | 完成 |
| 连接测试 | POST /api/debug/connect | 完成 |

## AI 工作流

- `analyze`：9 个节点（预处理→提取→完整性检查→冲突检测→目的地推荐→JSON 校验→合并）
- `generate`：12 个节点（需求验证→Prompt 构建→行程生成→推荐内容→JSON 修复校验→合并）
- `chat`：6 个节点（框架已搭好，等待实现）
- `rental`：1 个节点（仅租车服务点解析，无 Controller）

## 租车模块

数据库 `ai_travel` 包含 5 张租车表（rental_vehicle_group / rental_vehicle_model / rental_price_template / rental_pickup_poi / rental_order），已填充真实数据。后端仅实现 `RentalStoreResolveNode`，无 API。Flask 演示页独立展示数据（`/rental/demo`）。

## 代码分层

```
com.sora.aitravel
├── controller/     # 15 个 Controller
├── service/        # 业务接口 + impl
├── mapper/         # MyBatis-Plus Mapper
├── entity/         # 18 张表实体
├── dto/            # request / response / model
├── workflow/       # AI 工作流编排
├── prompt/         # Prompt 模板
├── config/         # Spring 配置
├── tools/          # AI Tool 定义
└── common/         # 枚举 / 异常 / 结果封装
```

## 数据库

18 张表：admin_operation_log / ai_call_log / ai_conversation / destination / file_resource / note / note_comment / note_favorite / note_like / note_tag / sys_user / tag / trip / rental_vehicle_group / rental_vehicle_model / rental_price_template / rental_pickup_poi / rental_order

## 环境

复制 `.env.example` → `.env`。`mvn clean package` 编译，`mvn spotless:apply` 格式化。
