# AI 智行伴旅后端

一期 Spring Boot 后端骨架，基础包名为 `com.sora.aitravel`，接口前缀为 `/api`。

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
