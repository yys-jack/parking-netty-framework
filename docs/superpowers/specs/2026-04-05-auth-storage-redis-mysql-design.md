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
3. 不修改现有的 HTTP 端口和 Actuator 配置

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
│                     认证查询流程（响应式）                    │
├─────────────────────────────────────────────────────────────┤
│  type=in-memory → Mono.just(map.get(serial))               │
│  type=redis     → ReactiveRedisTemplate.opsForHash()       │
│  type=jdbc      → 查数据库 SELECT（阻塞，需隔离线程）        │
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
│  │  ReactiveAuthRepository (接口)                          │ │
│  │  - Mono<AuthInfo> findBySerial(String serial)          │ │
│  │  - Mono<Boolean> validate(String serial, String secret)│ │
│  └────────────────────────────────────────────────────────┘ │
│                           ↑                                  │
│         ┌─────────────────┼─────────────────┐               │
│         │                 │                 │               │
│  ┌──────┴──────┐   ┌──────┴──────┐  ┌──────┴──────┐        │
│  │ InMemory    │   │  Redis      │  │  Jdbc       │        │
│  │ AuthRepo    │   │  AuthRepo   │  │  AuthRepo   │        │
│  │ (现有改造)   │   │  (新增)     │  │  (新增)     │        │
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
│  │  - Mono<Integer> refreshFromDatabaseToRedis()          │ │
│  └────────────────────────────────────────────────────────┘ │
│                                                              │
│  ┌────────────────────────────────────────────────────────┐ │
│  │  AuthRefreshController (新增 REST Controller)          │ │
│  │  - POST /api/auth/refresh                              │ │
│  └────────────────────────────────────────────────────────┘ │
│                                                              │
│  ┌────────────────────────────────────────────────────────┐ │
│  │  AuthHandler (改造为响应式处理)                         │ │
│  │  - 使用 Mono.flatMap() 链式调用认证逻辑                │ │
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

改为响应式接口：

```java
public interface ReactiveAuthRepository {
    /**
     * 根据序列号获取认证信息（响应式）
     */
    Mono<AuthInfo> findBySerial(String serial);

    /**
     * 验证认证信息（响应式）
     */
    Mono<Boolean> validate(String serial, String secret);
}
```

通过 Spring 的 `@ConditionalOnProperty` 决定激活哪个实现：

- `InMemoryAuthRepository`: 现有实现改造，返回 `Mono.just()`
- `RedisAuthRepository`: 新增 Redis 响应式实现
- `JdbcAuthRepository`: 新增数据库实现（阻塞式，需隔离线程）

### 3.4.1 InMemoryAuthRepository 设计（响应式包装）

```java
@Component
@ConditionalOnProperty(prefix = "auth.storage", name = "type", havingValue = "in-memory", matchIfMissing = true)
public class InMemoryAuthRepository implements ReactiveAuthRepository {
    
    private final Map<String, AuthInfo> authInfoMap;
    
    public InMemoryAuthRepository(AuthProperties properties) {
        this.authInfoMap = properties.getEntries().stream()
            .map(entry -> new AuthInfo(entry.getSerial(), entry.getParkCode(), entry.getSecret()))
            .collect(Collectors.toMap(AuthInfo::getSerial, Function.identity()));
    }
    
    @Override
    public Mono<AuthInfo> findBySerial(String serial) {
        return Mono.justOrEmpty(authInfoMap.get(serial));
    }
    
    @Override
    public Mono<Boolean> validate(String serial, String secret) {
        return findBySerial(serial)
            .map(info -> info.getSecret().equals(secret));
    }
}
```

**改造要点**：
- 使用 `Mono.justOrEmpty()` 包装内存查询结果
- 保持现有构造函数逻辑不变
- 响应式方法不调用 `subscribe()`，由调用方决定何时订阅

### 3.5 RedisAuthRepository 设计（响应式）

```java
@Component
@ConditionalOnProperty(prefix = "auth.storage", name = "type", havingValue = "redis")
public class RedisAuthRepository implements ReactiveAuthRepository {
    
    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private final String keyPrefix;
    
    @Override
    public Mono<AuthInfo> findBySerial(String serial) {
        String key = keyPrefix + serial;
        return redisTemplate
            .opsForHash()
            .multiGet(key, Arrays.asList("parkCode", "secret"))
            .map(values -> {
                if (values == null || values.isEmpty()) {
                    return null;
                }
                return new AuthInfo(serial, values.get(0), values.get(1));
            });
    }
    
    @Override
    public Mono<Boolean> validate(String serial, String secret) {
        return findBySerial(serial)
            .map(info -> info != null && info.getSecret().equals(secret));
    }
    
    // 内部方法：用于刷新缓存
    public Mono<Void> refreshCache(List<AuthInfo> authInfos) {
        // 使用 Redis reactive pipeline 批量写入
    }
    
    @PostConstruct
    public void init() {
        // 从数据库加载数据到 Redis（阻塞式启动加载）
    }
}
```

### 3.6 JdbcAuthRepository 设计（阻塞式，包装为响应式）

```java
@Component
@ConditionalOnProperty(prefix = "auth.storage", name = "type", havingValue = "jdbc")
public class JdbcAuthRepository implements ReactiveAuthRepository {
    
    private final JdbcTemplate jdbcTemplate;
    private final Scheduler jdbcScheduler;
    private final String tableName;
    
    // 使用 BoundedElastic Scheduler 隔离阻塞式数据库查询
    @Override
    public Mono<AuthInfo> findBySerial(String serial) {
        return Mono.fromCallable(() -> {
                String sql = "SELECT serial, park_code, secret FROM auth_info WHERE serial = ? AND enabled = 1";
                return jdbcTemplate.query(sql, this::mapRow, serial);
            })
            .subscribeOn(Schedulers.boundedElastic())  // 隔离到专用线程池
            .map(list -> list.isEmpty() ? null : list.get(0));
    }
    
    @Override
    public Mono<Boolean> validate(String serial, String secret) {
        return findBySerial(serial)
            .map(info -> info != null && info.getSecret().equals(secret));
    }
}
```

### 3.7 启动时数据加载

**RedisAuthRepository 的启动流程（响应式初始化）**：

```java
@Component
@ConditionalOnProperty(prefix = "auth.storage", name = "type", havingValue = "redis")
public class RedisAuthRepository implements ReactiveAuthRepository {
    
    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private final AuthJdbcProperties jdbcProperties;
    private final JdbcTemplate jdbcTemplate;  // 启动时阻塞式读取数据库
    
    @PostConstruct
    public void init() {
        // 1. 连接数据库（阻塞式，仅在启动时）
        // 2. 查询所有启用的认证信息：SELECT serial, park_code, secret FROM auth_info WHERE enabled = 1
        // 3. 使用 Redis reactive pipeline 批量写入：
        //    RedisCommand command = redisTemplate.createCommand();
        //    for (AuthInfo info : authInfos) {
        //        command.hset(keyPrefix + info.getSerial(), "parkCode", info.getParkCode());
        //        command.hset(keyPrefix + info.getSerial(), "secret", info.getSecret());
        //    }
        //    command.execute();
        log.info("从数据库加载 {} 条认证信息到 Redis", count);
    }
    
    /**
     * 刷新缓存（供刷新接口调用）
     */
    public Mono<Integer> refreshCache() {
        return Mono.fromCallable(() -> {
                // 从数据库查询所有启用的认证信息
                List<AuthInfo> authInfos = jdbcTemplate.query(...);
                // 清空旧缓存：DEL key-prefix*
                // 批量写入新缓存
                return authInfos.size();
            })
            .subscribeOn(Schedulers.boundedElastic())
            .thenMany(redisTemplate.execute(command -> {
                // 使用 pipeline 批量写入
            }))
            .thenReturn(count);
    }
}
```

### 3.8 刷新缓存接口设计

**AuthRefreshController（响应式 WebFlux）**:

```java
@RestController
@RequestMapping("/api/auth")
@ConditionalOnProperty(prefix = "auth.storage", name = "type", havingValue = "redis")
public class AuthRefreshController {
    
    private final String adminToken;
    private final RedisAuthRepository redisAuthRepository;
    
    @PostMapping("/refresh")
    public Mono<ResponseEntity<RefreshResponse>> refresh(
        @RequestHeader("X-Admin-Token") String adminToken
    ) {
        if (!this.adminToken.equals(adminToken)) {
            return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(RefreshResponse.error("Unauthorized: invalid admin token")));
        }
        
        return redisAuthRepository.refreshCache()
            .map(count -> ResponseEntity.ok(RefreshResponse.success(count)))
            .onErrorResume(ex -> {
                log.error("刷新缓存失败", ex);
                return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(RefreshResponse.error(ex.getMessage())));
            });
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

**注意**：由于项目当前使用的是 `spring-boot-starter-web`（阻塞式 MVC），而 Redis 使用响应式 API，有两种选择：

1. **保持阻塞式 Controller**：在 Controller 中调用 `block()` 等待响应式操作完成
   - 优点：不需要引入 WebFlux，改动最小
   - 缺点：刷新接口是阻塞的（但这是管理操作，影响不大）

2. **引入 WebFlux**：添加 `spring-boot-starter-webflux`，使用响应式 Controller
   - 优点：端到端响应式，架构一致
   - 缺点：增加依赖，需要配置 WebFlux

**推荐方案 1（保持阻塞式 Controller）**：
- 刷新接口是管理操作，调用频率低，阻塞影响可忽略
- 认证路径（AuthHandler）保持纯响应式，确保高并发性能

### 3.9 错误处理

| 场景 | 处理方式 |
|------|----------|
| Redis 连接失败 | 服务启动失败，抛出异常并记录日志 |
| 数据库连接失败 | 服务启动失败，抛出异常并记录日志 |
| 刷新接口 token 无效 | 返回 401 Unauthorized |
| 刷新时数据库查询失败 | 返回 500 Internal Server Error，记录详细日志 |
| Redis 写入失败 | 返回 500 Internal Server Error，记录详细日志 |
| 认证时 Redis 查询返回空 | `Mono.empty()`，AuthHandler 返回认证失败 |

### 3.10 AuthHandler 改造（响应式）

**原有设计（阻塞式）**：
```java
@Override
protected void channelRead0(ChannelHandlerContext ctx, Transmission msg) {
    boolean isValid = authRepository.validate(serial, secret);  // 阻塞
    if (isValid) {
        // ...
    }
}
```

**改造后（响应式）**：
```java
@Component
@Sharable
public class AuthHandler extends SimpleChannelInboundHandler<Transmission> {

    private final ReactiveAuthRepository authRepository;

    public AuthHandler(ReactiveAuthRepository authRepository) {
        this.authRepository = authRepository;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Transmission msg) {
        if (msg.getType() != Transmission.MessageType.TYPE_AUTHENTICATE) {
            ctx.channel().close();
            return;
        }

        Authenticate authenticate = Authenticate.parseFrom(msg.getMessage());
        
        // 使用响应式认证，订阅结果
        authRepository.validate(authenticate.getSerial(), authenticate.getSecret())
            .subscribe(
                isValid -> handleValid(ctx, msg, authenticate),
                error -> handleError(ctx, error)
            );
    }

    private void handleValid(ChannelHandlerContext ctx, Transmission msg, Authenticate authenticate) {
        authRepository.findBySerial(authenticate.getSerial())
            .subscribe(authInfo -> {
                // 认证成功，发送响应并移除 Handler
                // ...
            });
    }
}
```

**注意**：由于 Netty 的 `channelRead0` 是 `void` 方法，无法直接返回 `Mono`，需要在方法内部 `subscribe()` 处理结果。这是一种常见的响应式与事件驱动混合模式。

## 4. 依赖设计

### 4.1 新增依赖

```gradle
// Redis（响应式）
implementation 'org.springframework.boot:spring-boot-starter-data-redis-reactive'

// MySQL
implementation 'org.springframework.boot:spring-boot-starter-jdbc'
implementation 'com.mysql:mysql-connector-j'  // MySQL 8.x 驱动
```

**注意**：使用 `spring-boot-starter-data-redis-reactive` 而不是 `spring-boot-starter-data-redis`，前者包含 `ReactiveRedisTemplate` 和 Lettuce 响应式客户端。

### 4.2 可选依赖（推荐）

```gradle
// Redis 连接池（Lettuce 默认使用 Netty，已内置连接池）
// 如果使用 Redis Cluster 或需要更多控制，可添加：
implementation 'org.apache.commons:commons-pool2'
```

## 5. 测试设计

### 5.1 单元测试

- `RedisAuthRepositoryTest`: 测试 Redis 响应式实现的认证逻辑（使用 Testcontainers Redis）
- `JdbcAuthRepositoryTest`: 测试数据库实现的认证逻辑（使用 Testcontainers MySQL）
- `InMemoryAuthRepositoryTest`: 现有测试改造，验证响应式包装
- `AuthRefreshControllerTest`: 测试刷新接口的权限验证和刷新逻辑
- `AuthHandlerTest`: 测试响应式 AuthHandler 的消息处理

### 5.2 集成测试

- `AuthStorageIntegrationTest`: 测试不同存储类型的端到端认证流程
- 测试场景：
  1. in-memory 模式：现有测试保持不变
  2. redis 模式：启动 Redis，验证认证流程（响应式）
  3. jdbc 模式：启动 MySQL，验证认证流程

### 5.3 刷新接口测试

```bash
# 成功刷新
curl -X POST -H "X-Admin-Token: change-me-in-production" http://localhost:8080/api/auth/refresh

# 失败：token 无效
curl -X POST -H "X-Admin-Token: wrong-token" http://localhost:8080/api/auth/refresh
```

### 5.4 响应式测试要点

- 使用 `StepVerifier` 测试 `Mono` 和 `Flux`
- 使用 `VirtualTimeScheduler` 测试超时和延迟场景
- 使用 Testcontainers 提供真实的 Redis 和 MySQL 环境

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

## 7. 响应式编程要点

### 7.1 为什么选择响应式 Redis

| 对比项 | 阻塞式 Redis | 响应式 Redis |
|--------|-------------|-------------|
| API | `RedisTemplate` | `ReactiveRedisTemplate` |
| 返回类型 | `T` | `Mono<T>` / `Flux<T>` |
| 线程模型 | 阻塞调用线程 | 非阻塞，事件驱动 |
| Netty 集成 | I/O 线程可能被阻塞 | 完全非阻塞，适合高并发 |
| 代码复杂度 | 简单 | 较复杂（需要理解 Mono/Flux） |

### 7.2 响应式编程模型

```java
// 链式调用示例
authRepository.validate(serial, secret)
    .flatMap(isValid -> {
        if (!isValid) {
            return Mono.error(new AuthenticationException("Invalid credentials"));
        }
        return authRepository.findBySerial(serial);
    })
    .map(AuthInfo::getParkCode)
    .doOnNext(parkCode -> ChannelMaintainer.add(parkCode, channel))
    .subscribe(
        parkCode -> sendSuccessAndRemoveHandler(parkCode),
        error -> handleCloseChannel(error)
    );
```

### 7.3 注意事项

1. **避免在 I/O 线程中阻塞**：不要调用 `block()`，使用 `subscribe()` 处理结果
2. **异常处理**：使用 `onErrorResume()`、`onErrorReturn()` 处理错误
3. **资源管理**：确保订阅正确释放，避免内存泄漏
4. **测试**：使用 `StepVerifier` 验证 `Mono`/`Flux` 行为

## 8. 配置示例

### 8.1 开发环境（in-memory）

```yaml
auth:
  storage:
    type: in-memory
```

### 8.2 生产环境（redis）

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

### 8.3 生产环境（jdbc，无 Redis）

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

## 9. 验收标准

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
6. **响应式架构**：
   - `ReactiveAuthRepository` 接口返回 `Mono` 类型
   - Redis 操作使用 `ReactiveRedisTemplate`
   - AuthHandler 使用非阻塞 subscribe() 处理结果

## 10. 后续扩展建议

1. **多 Redis 实例支持**：支持 Redis Cluster 或 Sentinel 模式
2. **本地缓存**：在 Redis 基础上增加 Caffeine 本地缓存，进一步降低延迟
3. **审计日志**：记录刷新缓存的操作日志
4. **配置热更新**：监听配置中心（如 Nacos）的配置变化，自动刷新
