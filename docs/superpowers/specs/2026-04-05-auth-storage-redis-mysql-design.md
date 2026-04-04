# 生产环境认证存储设计文档

**日期**: 2026-04-05  
**状态**: 待审批

## 1. 概述

### 1.1 背景

当前停车场 Netty 框架的认证存储使用内存实现（`InMemoryAuthRepository`），从 `auth.yml` 配置文件加载认证信息。生产环境需要支持从数据库加载认证配置，并可选择使用 Redis 缓存以提升性能。

### 1.2 目标

1. 支持多种认证存储方式：内存（in-memory）、Redis、数据库（JDBC）
2. 通过配置决定使用哪种存储方式，默认为内存模式（保持现有行为）
3. 服务启动时，如配置为 Redis 模式，从 MySQL 加载认证配置到 Redis
4. 提供带权限控制的主动刷新缓存接口

### 1.3 非目标

1. 不支持运行时数据库到 Redis 的自动同步（刷新通过手动 API 触发）
2. 不涉及认证协议本身的修改
3. 不修改现有的 AuthHandler 认证逻辑（仅更换存储实现）

## 2. 架构设计

### 2.1 整体架构

```
┌─────────────────────────────────────────────────────────────┐
│                     服务启动流程                             │
├─────────────────────────────────────────────────────────────┤
│  1. 读取配置 auth.storage.type                              │
│  2. 如果 type=jdbc 或 type=redis:                           │
│     - 连接数据库，查询所有启用的认证信息                      │
│     - 如果 type=redis: 加载到 Redis                          │
│     - 如果 type=jdbc: 保留数据库连接供运行时查询              │
│  3. 如果 type=in-memory: 从 auth.yml 加载（现有行为）        │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│                     认证查询流程                             │
├─────────────────────────────────────────────────────────────┤
│  type=in-memory → 查内存 Map                                │
│  type=redis     → 查 Redis Hash                            │
│  type=jdbc      → 查数据库 SELECT                           │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│                     刷新缓存接口                             │
├─────────────────────────────────────────────────────────────┤
│  POST /api/auth/refresh                                     │
│  1. 验证 X-Admin-Token                                      │
│  2. 从数据库重新加载所有认证信息到 Redis                     │
│  3. 返回刷新结果                                            │
└─────────────────────────────────────────────────────────────┘
```

### 2.2 组件设计

```
┌──────────────────────────────────────────────────────────────┐
│                        Spring Context                        │
├──────────────────────────────────────────────────────────────┤
│  ┌────────────────────────────────────────────────────────┐ │
│  │  AuthRepository (接口)                                  │ │
│  │  - Optional<AuthInfo> findBySerial(String serial)      │ │
│  │  - boolean validate(String serial, String secret)      │ │
│  └────────────────────────────────────────────────────────┘ │
│                           ↑                                  │
│         ┌─────────────────┼─────────────────┐               │
│         │                 │                 │               │
│  ┌──────┴──────┐   ┌──────┴──────┐  ┌──────┴──────┐        │
│  │ InMemory    │   │  Redis      │  │  Jdbc       │        │
│  │ AuthRepo    │   │  AuthRepo   │  │  AuthRepo   │        │
│  │ (现有)      │   │  (新增)     │  │  (新增)     │        │
│  └─────────────┘   └─────────────┘  └─────────────┘        │
│                                                              │
│  ┌────────────────────────────────────────────────────────┐ │
│  │  AuthStorageProperties (配置属性)                       │ │
│  │  - type: in-memory | redis | jdbc                      │ │
│  │  - redis: host, port, password, database, key-prefix   │ │
│  │  - jdbc: url, username, password, table                │ │
│  │  - admin-token: 刷新接口的管理员 token                   │ │
│  └────────────────────────────────────────────────────────┘ │
│                                                              │
│  ┌────────────────────────────────────────────────────────┐ │
│  │  AuthRefresher (新增服务)                               │ │
│  │  - int refreshFromDatabaseToRedis()                    │ │
│  └────────────────────────────────────────────────────────┘ │
│                                                              │
│  ┌────────────────────────────────────────────────────────┐ │
│  │  AuthRefreshController (新增 REST Controller)          │ │
│  │  - POST /api/auth/refresh                              │ │
│  └────────────────────────────────────────────────────────┘ │
└──────────────────────────────────────────────────────────────┘
```

## 3. 详细设计

### 3.1 配置设计

#### 3.1.1 application.yml 新增配置

```yaml
auth:
  storage:
    type: in-memory  # in-memory | redis | jdbc，默认 in-memory
    admin-token: change-me-in-production  # 刷新接口的管理员 token
  redis:
    host: localhost
    port: 6379
    password: ${REDIS_PASSWORD:}  # 可选，支持环境变量
    database: 0
    key-prefix: parking:auth:
  jdbc:
    url: jdbc:mysql://localhost:3306/parking?useSSL=false&serverTimezone=UTC&characterEncoding=utf8
    username: root
    password: ${DB_PASSWORD:}
    table: auth_info
    driver-class-name: com.mysql.cj.jdbc.Driver
```

#### 3.1.2 配置属性类

- `AuthStorageProperties`: 存储类型和管理员 token
- `AuthRedisProperties`: Redis 连接配置
- `AuthJdbcProperties`: 数据库连接配置

### 3.2 数据库表结构

```sql
CREATE TABLE auth_info (
    id INT PRIMARY KEY AUTO_INCREMENT COMMENT '主键 ID',
    serial VARCHAR(64) NOT NULL UNIQUE COMMENT '设备序列号',
    park_code VARCHAR(64) NOT NULL COMMENT '停车场编码',
    secret VARCHAR(128) NOT NULL COMMENT '密钥',
    enabled TINYINT(1) DEFAULT 1 COMMENT '是否启用：1-启用，0-禁用',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_serial (serial),
    INDEX idx_park_code (park_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='认证信息表';
```

### 3.3 Redis 数据结构

**Hash 结构**：
- Key: `{key-prefix}{serial}` → 例如 `parking:auth:TEST-001`
- Field: `parkCode`, `secret`
- Value: 对应的值

**示例**：
```
HSET parking:auth:TEST-001 parkCode PARK001
HSET parking:auth:TEST-001 secret test-secret
HGETALL parking:auth:TEST-001
→ {parkCode: PARK001, secret: test-secret}
```

### 3.4 仓库接口设计

保持现有 `AuthRepository` 接口不变：

```java
public interface AuthRepository {
    Optional<AuthInfo> findBySerial(String serial);
    boolean validate(String serial, String secret);
}
```

通过 Spring 的 `@ConditionalOnProperty` 或 `@Profile` 决定激活哪个实现：

- `InMemoryAuthRepository`: 现有实现，标记为 `@ConditionalOnProperty(prefix="auth.storage", name="type", havingValue="in-memory")`
- `RedisAuthRepository`: 新增 Redis 实现
- `JdbcAuthRepository`: 新增数据库实现

### 3.5 RedisAuthRepository 设计

```java
@Component
@ConditionalOnProperty(prefix = "auth.storage", name = "type", havingValue = "redis")
public class RedisAuthRepository implements AuthRepository {
    
    private final RedisTemplate<String, String> redisTemplate;
    private final String keyPrefix;
    
    // findBySerial: HGETALL {key-prefix}{serial}
    // validate: HGET {key-prefix}{serial} secret 比较
    
    // @PostConstruct 时从数据库加载数据到 Redis
}
```

### 3.6 JdbcAuthRepository 设计

```java
@Component
@ConditionalOnProperty(prefix = "auth.storage", name = "type", havingValue = "jdbc")
public class JdbcAuthRepository implements AuthRepository {
    
    private final JdbcTemplate jdbcTemplate;
    private final String tableName;
    
    // findBySerial: SELECT * FROM auth_info WHERE serial = ? AND enabled = 1
    // validate: 同上，然后比较 secret
}
```

### 3.7 启动时数据加载

**RedisAuthRepository 的启动流程**：

```java
@Component
@ConditionalOnProperty(prefix = "auth.storage", name = "type", havingValue = "redis")
public class RedisAuthRepository implements AuthRepository {
    
    private final AuthJdbcProperties jdbcProperties;
    private final RedisTemplate<String, String> redisTemplate;
    
    @PostConstruct
    public void init() {
        // 1. 连接数据库
        // 2. 查询所有启用的认证信息：SELECT serial, park_code, secret FROM auth_info WHERE enabled = 1
        // 3. 逐个写入 Redis：HSET {key-prefix}{serial} parkCode {parkCode} secret {secret}
        log.info("从数据库加载 {} 条认证信息到 Redis", count);
    }
}
```

### 3.8 刷新缓存接口设计

**AuthRefreshController**:

```java
@RestController
@RequestMapping("/api/auth")
@ConditionalOnProperty(prefix = "auth.storage", name = "type", havingValue = "redis")
public class AuthRefreshController {
    
    @PostMapping("/refresh")
    public ResponseEntity<RefreshResponse> refresh(
        @RequestHeader("X-Admin-Token") String adminToken
    ) {
        // 1. 验证 adminToken 是否匹配配置
        // 2. 调用 AuthRefresher.refreshFromDatabaseToRedis()
        // 3. 返回结果
    }
}
```

**响应格式**：

```json
{
  "code": 1,
  "message": "success",
  "data": {
    "refreshed": 5
  }
}
```

**错误响应**：

```json
{
  "code": 2,
  "message": "Unauthorized: invalid admin token"
}
```

### 3.9 错误处理

| 场景 | 处理方式 |
|------|----------|
| Redis 连接失败 | 服务启动失败，抛出异常并记录日志 |
| 数据库连接失败 | 服务启动失败，抛出异常并记录日志 |
| 刷新接口 token 无效 | 返回 401 Unauthorized |
| 刷新时数据库查询失败 | 返回 500 Internal Server Error，记录详细日志 |
| Redis 写入失败 | 返回 500 Internal Server Error，记录详细日志 |

## 4. 依赖设计

### 4.1 新增依赖

```gradle
// Redis
implementation 'org.springframework.boot:spring-boot-starter-data-redis'

// MySQL
implementation 'org.springframework.boot:spring-boot-starter-jdbc'
implementation 'com.mysql:mysql-connector-j'  // MySQL 8.x 驱动
```

### 4.2 可选依赖（推荐）

```gradle
// Redis 连接池
implementation 'org.apache.commons:commons-pool2'
```

## 5. 测试设计

### 5.1 单元测试

- `RedisAuthRepositoryTest`: 测试 Redis 实现的认证逻辑（使用 Testcontainers 或 Embedded Redis）
- `JdbcAuthRepositoryTest`: 测试数据库实现的认证逻辑（使用 Testcontainers 或 H2）
- `AuthRefreshControllerTest`: 测试刷新接口的权限验证和刷新逻辑

### 5.2 集成测试

- `AuthStorageIntegrationTest`: 测试不同存储类型的端到端认证流程
- 测试场景：
  1. in-memory 模式：现有测试保持不变
  2. redis 模式：启动 Redis，验证认证流程
  3. jdbc 模式：启动 MySQL，验证认证流程

### 5.3 刷新接口测试

```bash
# 成功刷新
curl -X POST -H "X-Admin-Token: change-me-in-production" http://localhost:8080/api/auth/refresh

# 失败：token 无效
curl -X POST -H "X-Admin-Token: wrong-token" http://localhost:8080/api/auth/refresh
```

## 6. 迁移路径

### 6.1 从 in-memory 迁移到 redis

1. 部署 Redis 和 MySQL
2. 将现有 `auth.yml` 数据导入 MySQL
3. 修改 `application.yml`：
   ```yaml
   auth:
     storage:
       type: redis
   ```
4. 重启服务，验证 Redis 自动加载数据
5. 验证认证流程正常

### 6.2 数据库初始化脚本

```bash
# 从 auth.yml 导出并导入到 MySQL
java -cp build/libs/parking-netty-framework-1.0.0.jar cn.tnar.parking.tool.AuthYamlToMySql \
    --input src/main/resources/auth.yml \
    --url jdbc:mysql://localhost:3306/parking \
    --username root \
    --password xxx
```

（这是一个可选的工具类，用于辅助迁移）

## 7. 配置示例

### 7.1 开发环境（in-memory）

```yaml
auth:
  storage:
    type: in-memory
```

### 7.2 生产环境（redis）

```yaml
auth:
  storage:
    type: redis
    admin-token: ${AUTH_ADMIN_TOKEN}  # 从环境变量读取
  redis:
    host: ${REDIS_HOST}
    port: 6379
    password: ${REDIS_PASSWORD}
    database: 0
    key-prefix: parking:auth:
  jdbc:
    url: jdbc:mysql://${DB_HOST}:3306/parking?useSSL=false&serverTimezone=UTC
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
    table: auth_info
```

### 7.3 生产环境（jdbc，无 Redis）

```yaml
auth:
  storage:
    type: jdbc
    admin-token: ${AUTH_ADMIN_TOKEN}
  jdbc:
    url: jdbc:mysql://${DB_HOST}:3306/parking?useSSL=false&serverTimezone=UTC
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
    table: auth_info
```

## 8. 验收标准

1. **配置驱动**：通过 `auth.storage.type` 配置切换存储方式，默认 in-memory
2. **Redis 模式**：
   - 启动时自动从数据库加载认证信息到 Redis
   - 认证时只查 Redis，不查数据库
   - Redis Key 格式为 `parking:auth:{serial}`，使用 Hash 结构
3. **JDBC 模式**：认证时直接查数据库
4. **刷新接口**：
   - `POST /api/auth/refresh` 需要 `X-Admin-Token` 头
   - Token 验证通过后才执行刷新
   - 刷新成功后返回刷新的条目数
5. **向后兼容**：现有 in-memory 模式保持不变，现有测试全部通过

## 9. 后续扩展建议

1. **多 Redis 实例支持**：支持 Redis Cluster 或 Sentinel 模式
2. **本地缓存**：在 Redis 基础上增加 Caffeine 本地缓存，进一步降低延迟
3. **审计日志**：记录刷新缓存的操作日志
4. **配置热更新**：监听配置中心（如 Nacos）的配置变化，自动刷新
