

# PlanGo 后端

基于 Spring Boot 3.5 + Java 17 的 AI 智能旅游后端服务，提供行程规划、美食推荐、景点查询、租车预订等核心功能。

## 技术栈

- **后端框架**：Spring Boot 3.5
- **持久层**：MyBatis-Plus + MySQL
- **缓存**：Redis
- **消息队列**：RabbitMQ
- **认证**：Sa-Token
- **AI 能力**：Spring AI 1.1.8 + DeepSeek + DashScope Embedding
- **文件存储**：腾讯云 COS

## 项目结构

```
com.sora.aitravel
├── controller/      # REST API 控制器
├── service/         # 业务逻辑接口及实现
├── mapper/          # MyBatis Plus Mapper
├── entity/         # 数据库实体
├── dto/             # 数据传输对象
├── workflow/       # AI 工作流编排
├── tools/          # AI Tool 定义
├── config/         # Spring 配置
├── client/         # 第三方 API 客户端
└── common/         # 公共组件（枚举、异常、工具类）
```

## 核心功能

### API 模块

| 模块 | 说明 |
|------|------|
| 认证 | 邮箱验证码、注册、登录、退出 |
| 用户 | 获取资料、修改资料 |
| 行程 | 创建、列表、详情、修改、删除 |
| AI 分析 | 需求分析 + 目的地推荐（工作流） |
| AI 生成 | 智能行程规划生成（工作流） |
| 游记 | CRUD + 点赞、收藏、评论 |
| 目的地 | 列表、热门推荐 |
| 标签 | 标签管理 |
| 文件 | 腾讯云 COS 上传 |
| 租车 | 报价预览、订单创建、支付 |

### AI 工作流

- **analyze**：用户需求分析 → 信息提取 → 完整性检查 → 目的地推荐 → 冲突检测 → 结果合并
- **generate**：需求验证 → 数据获取 → 逐日规划 → JSON 校验 → 结果合并

### 工具（Tool）

- `FoodTool`：美食推荐（高德餐饮 POI）
- `ScenicTool`：景点查询
- `HotelTool`：酒店搜索
- `WeatherTool`：天气查询
- `AmapGeoTool`：地理编码/逆地理编码
- `AmapPoiTool`：POI 搜索
- `AmapRouteTool`：路线规划
- `AmapStaticMapTool`：静态地图生成
- `RentalStoreAiTool`：租车门店解析

## 快速开始

### 环境要求

- Java 17+
- Maven 3.8+
- MySQL 8.0+
- Redis 6.0+

### 配置

1. 复制并配置环境文件：
   ```bash
   cp .env.example .env
   ```

2. 修改 `application.yml` 中的数据库、Redis 等配置

### 编译运行

```bash
# 编译
mvn clean package

# 运行
mvn spring-boot:run

# 或直接运行 jar
java -jar target/ai-travel-*.jar
```

### 代码格式化

```bash
mvn spotless:apply
```

## 数据库

项目使用 18 张数据表，主要包括：

- `sys_user`：用户表
- `trip`：行程表
- `note`：游记表
- `destination`：目的地表
- `rental_*`：租车相关表（车辆组、车型、价格模板、取车点、订单）

初始化脚本位于 `src/main/resources/sql/`

## 接口前缀

所有 API 接口统一前缀：`/api`

## License

MIT License