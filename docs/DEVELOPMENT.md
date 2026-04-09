# 开发指南

**日期**: 2026-04-05  
**状态**: 已启用

---

## 1. 开发环境配置

### 1.1 JDK 安装

本项目使用 **Alibaba Dragonwell 21.0.10** (JDK 21)。

**下载地址**: https://github.com/alibaba/dragonwell/releases

**环境变量配置**:
```bash
export JAVA_HOME=/path/to/dragonwell-21.0.10
export PATH=$JAVA_HOME/bin:$PATH
```

**验证安装**:
```bash
java -version
# 应输出: openjdk version "21.0.10"
```

### 1.2 IDE 配置

**IntelliJ IDEA**:
1. 打开项目，选择 Gradle 自动导入
2. Settings → Build, Execution, Deployment → Build Tools → Gradle
   - Gradle JVM: 选择 JDK 21
3. Settings → Editor → Code Style → Java
   - 导入 `checkstyle.xml` 配置

**VS Code**:
1. 安装 Extension Pack for Java
2. 设置 `settings.json`:
```json
{
  "java.configuration.updateBuildConfiguration": "automatic",
  "java.project.referencedLibraries": ["**/*.jar"]
}
```

### 1.3 Git Hooks 安装

```bash
# 安装 pre-commit hook
./scripts/install-hooks.sh

# 验证安装
ls -la .git/hooks/pre-commit
```

---

## 3. 核心组件

---

## 2.1 数据库配置

项目支持 H2（默认）和 MySQL 两种数据库，通过 profile 切换：

```yaml
# application.yml
spring:
  profiles:
    active: h2  # 可选值：h2 | mysql
```

**切换方式**:
- 修改 `application.yml` 中的 `spring.profiles.active`
- 或命令行启动：`--spring.profiles.active=mysql`

**配置文件**:
- `application-h2.yml` - H2 嵌入式数据库（默认）
- `application-mysql.yml` - MySQL 数据库（生产环境）

## 2.2 项目结构

```
parking-netty-framework/
├── src/main/java/cn/tnar/parking/
│   ├── NettyApplication.java          # Spring Boot 入口
│   ├── config/                        # 配置类
│   ├── framework/                     # 框架核心
│   ├── netty/                         # Netty 核心
│   ├── repository/                    # 数据访问层
│   ├── task/                          # 任务调度
│   └── model/                         # 模型类
├── src/main/proto/
│   └── message.proto                  # Protobuf 定义
├── src/main/resources/
│   ├── application.yml                # 应用配置（默认 H2 profile）
│   ├── application-h2.yml             # H2 数据库配置
│   ├── application-mysql.yml          # MySQL 数据库配置
│   └── auth.yml                       # 认证配置
├── src/test/java/                     # 测试代码
├── scripts/                           # 工具脚本
└── build.gradle                       # Gradle 配置
```

---

## 4. 核心组件

### 3.1 Netty 服务器 (ApiServer)

```java
// Netty TCP 服务器配置
ServerBootstrap bootstrap = new ServerBootstrap();
bootstrap.group(bossGroup, workerGroup)
    .channel(NioServerSocketChannel.class)
    .childHandler(new ChannelInitializer<SocketChannel>() {
        @Override
        protected void initChannel(SocketChannel ch) {
            ChannelPipeline pipeline = ch.pipeline();
            pipeline.addLast(new LengthFieldBasedFrameDecoder(...));
            pipeline.addLast(new AuthHandler(authRepository));
            pipeline.addLast(new DispatcherHandler(handlerRegistry));
        }
    });
```

**职责**:
- TCP 服务端启动和关闭
- Channel 初始化和 Pipeline 配置
- 连接数统计和监控

### 3.2 消息分派器 (DispatcherHandler)

```java
@Override
protected void channelRead0(ChannelHandlerContext ctx, Transmission msg) {
    Handler handler = handlerRegistry.getHandler(msg.getType());
    if (handler == null) {
        log.warn("No handler for message type: {}", msg.getType());
        return;
    }
    Respond respond = handler.handle(msg, parkCode);
    ctx.writeAndFlush(respond);
}
```

**职责**:
- 根据消息类型查找对应 Handler
- 调用 Handler 处理消息
- 返回响应给客户端

### 3.3 Handler 接口

```java
public interface Handler {
    /**
     * 处理消息
     * @param transmission 传输消息
     * @param parkCode 停车场编码
     * @return 处理结果
     */
    Respond handle(Transmission transmission, String parkCode);
    
    /**
     * 返回此 Handler 处理的消息类型
     */
    Transmission.MessageType processedType();
}
```

### 3.4 认证机制 (AuthHandler)

```java
// 认证流程
if (messageType == TYPE_AUTHENTICATE) {
    AuthInfo authInfo = authRepository.findBySerial(serial);
    if (authInfo != null && authInfo.getSecret().equals(secret)) {
        // 认证成功，移除自身
        ctx.pipeline().remove(this);
    }
}
```

**认证超时**: 10 秒（可配置）

### 3.5 配置类

**AuthProperties** - 认证配置属性：

```java
@ConfigurationProperties("auth")
public class AuthProperties {
    private Storage storage = new Storage();
    private Redis redis = new Redis();
    private Jdbc jdbc = new Jdbc();
    // getters/setters
}
```

**RedisProperties** - Redis 配置属性：

```java
public static class Redis {
    private String host = "localhost";
    private int port = 6379;
    private String password;
    private int database = 0;
    private String keyPrefix = "parking:auth:";
    // getters/setters
}
```

**JdbcProperties** - JDBC 配置属性：

```java
public static class Jdbc {
    private String url;
    private String username;
    private String password;
    private String table = "auth_info";
    // getters/setters
}
```

---

## 5. 开发流程

### 5.1 添加新 Handler

**步骤 1**: 创建 Handler 类

```java
@Component
public class PaymentHandler implements Handler {
    
    private static final log = LoggerFactory.getLogger(PaymentHandler.class);
    
    @Override
    public Respond handle(Transmission transmission, String parkCode) {
        Payment payment;
        try {
            payment = Payment.parseFrom(transmission.getMessage());
        } catch (InvalidProtocolBufferException e) {
            log.error("解析支付消息失败", e);
            return RespondProto.failure("解析失败");
        }
        
        log.info("处理支付，parkCode: {}, 金额：{}", parkCode, payment.getAmount());
        
        // 业务逻辑
        // ...
        
        return RespondProto.success();
    }
    
    @Override
    public Transmission.MessageType processedType() {
        return Transmission.MessageType.TYPE_PAYMENT;
    }
}
```

**步骤 2**: 添加消息类型（如需要）

`message.proto`:
```protobuf
enum MessageType {
    // ... 现有类型
    TYPE_PAYMENT = 7;
}
```

**步骤 3**: 添加测试

```java
class PaymentHandlerTest {
    @Test
    void testPayment() {
        PaymentHandler handler = new PaymentHandler();
        Transmission transmission = ...;
        Respond respond = handler.handle(transmission, "PARK001");
        assertEquals(1, respond.getCode());
    }
}
```

**步骤 4**: 运行测试

```bash
./gradlew test --tests "PaymentHandlerTest"
```

---

### 5.2 添加新消息类型

**步骤 1**: 修改 proto 文件

`src/main/proto/message.proto`:
```protobuf
message Transmission {
  fixed32     ID      = 1;
  MessageType type    = 2;
  bytes       message = 3;
}

enum MessageType {
  TYPE_RESPOND = 1;
  TYPE_AUTHENTICATE = 2;
  TYPE_SPACE = 3;
  TYPE_CAR_IN = 5;
  TYPE_CAR_OUT = 6;
  TYPE_PAYMENT = 7;  // 新增
  TYPE_DEMO = 100;
}

message Payment {  // 新增
  fixed32 ID = 1;
  string orderNo = 2;
  decimal amount = 3;
  string payTime = 4;
}
```

**步骤 2**: 编译 Proto

```bash
./gradlew generateProto
```

**步骤 3**: 验证生成

```bash
ls build/generated/source/proto/main/java/cn/tnar/parking/model/
```

---

### 5.3 实现响应式认证存储

**场景**: 添加 Redis 响应式认证存储

**步骤 1**: 实现 ReactiveAuthRepository 接口

```java
@Repository
@RequiredArgsConstructor
public class RedisAuthRepository implements ReactiveAuthRepository {
    
    private final RedisTemplate<String, String> redisTemplate;
    private final String keyPrefix;
    
    @Override
    public Mono<AuthInfo> findBySerial(String serial) {
        String key = keyPrefix + serial;
        return redisTemplate.opsForHash()
            .multiGet(key, Arrays.asList("parkCode", "secret"))
            .flatMap(values -> {
                if (values == null || values.isEmpty()) {
                    return Mono.empty(); // 返回空 Mono，而非 null
                }
                AuthInfo authInfo = new AuthInfo(
                    serial,
                    values.get(0), // parkCode
                    values.get(1)  // secret
                );
                return Mono.just(authInfo);
            });
    }
    
    @Override
    public Mono<Boolean> validate(String serial, String secret) {
        return findBySerial(serial)
            .map(authInfo -> authInfo.getSecret().equals(secret))
            .defaultIfEmpty(false);
    }
    
    /**
     * 服务启动时从数据库加载数据到 Redis
     */
    @PostConstruct
    public void loadFromDatabase() {
        // 从 JDBC 加载数据到 Redis
    }
}
```

**步骤 2**: 切换存储类型

`application.yml`:
```yaml
auth:
  storage:
    type: redis  # 从 in-memory 改为 redis
```

**关键要点**:
- 返回 `Mono<T>` 而非直接返回值
- 使用 `Mono.empty()` 表示空值，而非 `null`
- 使用 `flatMap()` 进行链式调用
- 使用 `defaultIfEmpty()` 提供默认值

### 5.4 响应式编程指南

本项目使用 Project Reactor 实现响应式编程。

**核心概念**:

1. **Mono<T>** - 表示 0 或 1 个异步结果

```java
Mono<AuthInfo> result = repository.findBySerial("TEST-001");
```

2. **空值处理** - 使用 `Mono.empty()` 而非 `null`

```java
// 正确
return Mono.empty();

// 错误 - 会导致 NullPointerException
return Mono.just(null);
```

3. **阻塞操作隔离** - 由 `databaseScheduler` 统一调度

```java
// 在 Repository 层无需 subscribeOn，由 DispatcherHandler 统一管理
@Override
public Mono<AuthInfo> findBySerial(String serial) {
    return Mono.fromCallable(() -> {
        // 阻塞式 JDBC 调用
        return jdbcTemplate.queryForObject(...);
    });
    // databaseScheduler 在 DispatcherHandler 层统一调度
}
```

4. **异步结果处理** - 使用 `subscribe()` 处理最终结果

```java
authRepository.validate(serial, secret)
    .subscribe(
        isValid -> {
            // 验证成功
            ctx.writeAndFlush(response);
        },
        error -> {
            // 处理异常
            log.error("Validation failed", error);
        }
    );
```

**测试响应式代码**:

使用 `StepVerifier` 测试 `Mono` 和 `Flux`:

```java
@Test
void findBySerial_whenExists_returnsAuthInfo() {
    Mono<AuthInfo> result = repository.findBySerial("TEST-001");
    StepVerifier.create(result)
        .assertNext(auth -> assertEquals("PARK001", auth.getParkCode()))
        .verifyComplete();
}

@Test
void findBySerial_whenNotExists_returnsEmpty() {
    Mono<AuthInfo> result = repository.findBySerial("NOT-EXISTS");
    StepVerifier.create(result)
        .verifyComplete(); // 验证 Mono.empty()
}
```

### 5.5 统一调度器架构

**日期**: 2026-04-06

本项目采用统一调度器架构，所有数据库相关操作由 `databaseScheduler` 集中管理。

**架构演进**:

早期实现中，每个 Repository 层都使用 `Schedulers.boundedElastic()` 进行线程隔离：

```
DispatcherHandler (无调度器)
    ↓
AbstractHandler (subscribeOn boundedElastic)  ← 冗余
    ↓  
Repository (subscribeOn boundedElastic)       ← 冗余
```

实际上，只有最内层的 `subscribeOn` 生效，外层的调度器被覆盖，导致配置无法统一管理。

**当前架构**:

重构后，所有数据库操作由 `databaseScheduler` 统一调度：

```
DispatcherHandler (subscribeOn databaseScheduler)  ← 统一调度点
    ↓
AbstractHandler (无调度器)
    ↓
Repository (无调度器)
```

**优势**:
- **可配置**: 通过 `scheduler.database.*` 配置线程池参数
- **可监控**: 统一线程池便于观察和调优
- **更清晰**: 调度责任明确，代码更易维护

**配置示例**:

```yaml
scheduler:
  database:
    core-threads: 10
    max-threads: 50
    queue-capacity: 100
```

**代码示例**:

```java
// Repository 层 - 无需 subscribeOn
@Repository
public class JdbcAuthRepository implements ReactiveAuthRepository {
    
    private final JdbcTemplate jdbcTemplate;
    
    @Override
    public Mono<AuthInfo> findBySerial(String serial) {
        return Mono.fromCallable(() -> {
            // JDBC 查询逻辑
            return jdbcTemplate.queryForObject(...);
        });
        // 由 DispatcherHandler 的 databaseScheduler 统一调度
    }
}
```

```java
// DispatcherHandler 层 - 统一调度点
@Component
public class DispatcherHandler extends SimpleChannelInboundHandler<Transmission> {
    
    private final Scheduler databaseScheduler;
    
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Transmission msg) {
        // ...
        responseMono
            .subscribeOn(databaseScheduler)  // 统一调度点
            .subscribe(...);
    }
}
```

### 5.6 监控指标开发指南

本项目使用 Micrometer + Prometheus + Grafana 构建监控体系。

**使用 MetricsAspect 收集指标**:

```java
@Autowired
private MetricsAspect metricsAspect;

// 认证成功
metricsAspect.recordAuthSuccess(parkCode, durationSeconds);

// 认证失败
metricsAspect.recordAuthFailure(parkCode, reason, durationSeconds);

// 消息处理
metricsAspect.recordMessage(messageType, parkCode, durationSeconds, success);
```

**指标说明**:

| 指标名称 | 类型 | 说明 | 标签 |
|---------|------|------|------|
| `parking_netty_auth_success_total` | Counter | 认证成功次数 | `park_code` |
| `parking_netty_auth_failure_total` | Counter | 认证失败次数 | `park_code`, `reason` |
| `parking_netty_auth_duration_seconds` | Histogram | 认证耗时 | `park_code` |
| `parking_netty_messages_total` | Counter | 消息处理总数 | `message_type`, `park_code` |
| `parking_netty_messages_failure_total` | Counter | 消息处理失败数 | `message_type`, `park_code` |
| `parking_netty_message_duration_seconds` | Histogram | 消息处理耗时 | `message_type`, `park_code` |

**自定义指标配置**:

在 `MetricsConfig` 中配置通用标签：

```java
@Configuration
public class MetricsConfig {

    @Bean
    public MeterRegistryCustomizer<MeterRegistry> metricsCommonTags() {
        return registry -> registry.config()
            .commonTags("application", "parking-netty");
    }
}
```

**自定义指标**:

```java
@Autowired
private MeterRegistry meterRegistry;

// 计数器
meterRegistry.counter("custom_metric_total", "tag_key", "tag_value").increment();

// 计时器
meterRegistry.timer("custom_duration_seconds", "tag_key", "tag_value")
    .record(durationMs, TimeUnit.MILLISECONDS);

// 规范（Gauge）
meterRegistry.gauge("custom_gauge", object, obj -> obj.getValue());
```

### 5.7 消息幂等性

消息幂等性由 `IdempotencyGuard` 实现，基于滑动窗口去重：

| 维度 | 要求 |
|------|------|
| **范围** | 同一 TCP 连接 |
| **判重键** | `(parkCode, messageId)` 组合 |
| **时间窗口** | 10 秒滑动窗口 |
| **重复响应** | `CODE_DUPLICATE` |

**使用方式**:
```java
// IdempotencyGuard 自动在 DispatcherHandler 层拦截
// Handler 无需关心幂等性逻辑
```

**响应码**:
```protobuf
enum RespondCode {
  CODE_DUPLICATE = 3;  // 重复的消息
  // ... 其他响应码
}
```

**监控指标**:
- `parking_netty_duplicates_total` - 重复消息计数
- 标签：`message_type`, `park_code`

**示例**: 客户端发送相同 ID 的消息
```
t=0s: 发送 ID=100 → 成功处理
t=5s: 发送 ID=100 → 返回 CODE_DUPLICATE
t=15s: 发送 ID=100 → 成功处理（已超出 10 秒窗口）
```

---

## 6. 测试

### 6.1 单元测试

```java
@ExtendWith(MockitoExtension.class)
class CarInHandlerTest {
    
    @Mock
    private CarInRepository carInRepository;
    
    @InjectMocks
    private CarInHandler handler;
    
    @Test
    void testCarIn() {
        // 准备数据
        CarIn carIn = CarIn.newBuilder()
            .setPlateNo("陕 A12345")
            .setChannelId("ENTRANCE-01")
            .build();
        
        Transmission transmission = Transmission.newBuilder()
            .setMessage(carIn.toByteString())
            .setType(Transmission.MessageType.TYPE_CAR_IN)
            .build();
        
        // 执行
        Respond respond = handler.handle(transmission, "PARK001");
        
        // 验证
        assertEquals(1, respond.getCode());
        verify(carInRepository).save(any());
    }
}
```

### 5.2 集成测试

```java
@SpringBootTest
@AutoConfigureEmbeddedDatabase
class AuthIntegrationTest {
    
    @Autowired
    private AuthRepository authRepository;
    
    @Test
    void testAuthentication() {
        AuthInfo authInfo = authRepository.findBySerial("TEST-001");
        assertNotNull(authInfo);
        assertEquals("test-secret", authInfo.getSecret());
    }
}
```

### 6.3 测试客户端

```bash
# 运行测试客户端
./gradlew runClient \
    -PclientHost=localhost \
    -PclientPort=8889 \
    -PclientSerial=TEST-001 \
    -PclientSecret=test-secret
```

---

## 7. 代码规范

### 7.1 命名规范

| 类型 | 规范 | 示例 |
|------|------|------|
| 类名 | 大驼峰 | `CarInHandler`, `AuthRepository` |
| 方法名 | 小驼峰 | `handle()`, `findBySerial()` |
| 变量名 | 小驼峰 | `parkCode`, `transmission` |
| 常量名 | 全大写 + 下划线 | `CODE_SUCCESS`, `MAX_RETRY` |
| 包名 | 全小写 | `cn.tnar.parking.netty.handler` |

### 7.2 代码风格

**行长度**: ≤ 120 字符

**方法长度**: ≤ 100 行

**参数数量**: ≤ 8 个

**圈复杂度**: ≤ 10

**检查命令**:
```bash
./gradlew checkstyleMain
```

### 7.3 日志规范

```java
// 使用 SLF4J
private static final log = LoggerFactory.getLogger(MyClass.class);

// 级别选择
log.trace("详细追踪信息");  // 开发调试
log.debug("调试信息");     // 开发环境
log.info("重要业务日志"); // 生产环境
log.warn("警告信息");     // 潜在问题
log.error("错误信息", e); // 异常错误
```

---

## 8. 故障排查

### 8.1 启动失败

**问题**: `Address already in use`

**解决**:
```bash
# 检查端口占用
netstat -tlnp | grep 8889

# 杀死占用进程
kill -9 <PID>

# 或修改配置
# application.yml
netty:
  tcp:
    port: 8890  # 换个端口
```

### 8.2 连接失败

**问题**: `Connection refused`

**排查步骤**:
1. 检查服务端是否启动：`docker ps` 或 `ps aux | grep java`
2. 检查防火墙：`telnet localhost 8889`
3. 查看服务端日志：`docker logs netty-server-1`

### 8.3 认证失败

**问题**: `CODE_MISMATCH` 或 `CODE_FAILURE`

**排查**:
1. 检查 `auth.yml` 配置
2. 确认 serial/secret 匹配
3. 查看日志中的认证错误

```yaml
# auth.yml
auth:
  entries:
    - serial: TEST-001
      parkCode: PARK001
      secret: test-secret  # 确保客户端使用相同的 secret
```

### 8.4 消息未处理

**问题**: 客户端发送消息后无响应

**排查**:
1. 检查 Handler 是否注册到 Spring 容器（添加 `@Component`）
2. 确认 `processedType()` 返回正确的消息类型
3. 查看 DispatcherHandler 日志

---

## 9. 常用命令

### 9.1 构建和运行

```bash
# 编译 Proto
./gradlew generateProto

# 编译项目
./gradlew build

# 运行应用
./gradlew bootRun

# 打包
./gradlew bootJar
```

### 8.2 测试

```bash
# 快速测试
./gradlew test

# 运行特定测试
./gradlew test --tests "CarInHandlerTest"

# 完整测试套件
./scripts/test-all.sh

# 查看测试报告
open build/reports/tests/test/index.html
```

### 9.3 代码质量

```bash
# Checkstyle 检查
./gradlew checkstyleMain checkstyleTest

# 覆盖率报告
./gradlew jacocoTestReport

# 查看报告
open build/reports/checkstyle/main.html
open build/reports/jacoco/test/html/index.html
```

### 9.4 Docker

```bash
# 构建镜像
./scripts/build-docker.sh

# 启动
./scripts/start.sh

# 查看状态
docker compose ps

# 查看日志
docker compose logs -f

# 停止
./scripts/stop.sh
```

---

## 10. 性能优化

### 10.1 Netty 配置

```yaml
netty:
  tcp:
    port: 8889
    backlog: 256        # 连接队列大小
    heartbeat-interval: 90  # 心跳间隔
```

### 10.2 JVM 参数

```bash
java -Xms512m -Xmx2g -XX:+UseG1GC -jar app.jar
```

### 10.3 连接管理

- 启用心跳检测，及时清理空闲连接
- 设置合理的认证超时时间
- 监控 Channel 数量

---

## 11. 最佳实践

1. **使用 AbstractHandler** - 统一的日志和异常处理
2. **异常处理** - 使用 BusinessException 处理业务异常
3. **异步处理** - 使用 OperationResultFuture 处理异步操作
4. **Repository 模式** - 数据访问层使用 Repository 模式
5. **配置驱动** - 使用配置类管理配置项
6. **频繁测试** - 每次修改后运行测试
7. **代码审查** - 提交前运行完整测试套件
