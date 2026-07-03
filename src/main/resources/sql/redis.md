# Redis 配置与数据说明

## 连接配置

| 参数 | 值 |
|------|------|
| Host | `${REDIS_HOST}`（默认 `127.0.0.1`） |
| Port | `${REDIS_PORT}`（默认 `6379`） |
| Password | `${REDIS_PASSWORD}` |

项目使用 Spring Data Redis，有两个客户端：
- `RedisTemplate<String, Object>` — 通用缓存，JSON 序列化
- `StringRedisTemplate` — 字符串 KV（验证码等）

## Redis Key 常量

定义在 `RedisKeyConstants.java`。

| Key 模式 | 用途 | TTL |
|----------|------|-----|
| `email:code:{scene}:{email}` | 邮箱验证码 | 5 分钟 |
| `email:code:limit:{scene}:{email}` | 验证码发送限流 | 60 秒 |

## 实际 Redis Keys

### 1. 高德 POI 查询缓存
由 `AmapPoiCacheServiceImpl` 管理。
- `amap:poi:v1:{city}:{type}:{count}:{hash}` — 按类型召回 POI
- `amap:poi:v2:{city}:{query}:{typecode}:{page}:{offset}:{hash}` — 关键词搜索 POI

### 2. 登录认证（Sa-Token）
- `Authorization:login:token:{token}` — Token-Session
- `Authorization:login:session:{userId}` — User-Session

### 3. 邮箱验证码
- `email:code:register:{email}` — 注册验证码
- `email:code:limit:register:{email}` — 发送频率锁

### 4. AI 行程生成 StateGraph 检查点
- `graph:checkpoint:content:{uuid}` — 生成过程中的中间状态持久化

## 首页缓存预热
启动时 `HomeCacheWarmer` 自动加载首页热门数据到内存。
