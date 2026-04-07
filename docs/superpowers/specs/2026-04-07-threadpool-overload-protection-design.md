# 线程池过载保护设计文档

**日期**: 2026-04-07  
**状态**: 设计中

---

## 1. 问题描述

### 1.1 背景

当前线程池架构使用 `ThreadPoolExecutor.AbortPolicy` 作为拒绝策略。当数据库线程池队列满时：

1. 线程池抛出 `RejectedExecutionException`
2. 异常在 Reactor 的 `subscribe()` 回调中抛出
3. **可能导致 Netty IO 线程被阻塞或影响正常消息处理**

### 1.2 现状配置

```java
// SchedulerConfig.java 当前配置
new ThreadPoolExecutor(
    properties.getCoreThreads(),      // database: 20
    properties.getMaxThreads(),       // database: 100
    60L, TimeUnit.SECONDS,
    new LinkedBlockingQueue<>(properties.getQueueCapacity()), // 5000
    new ThreadFactoryBuilder()
        .setNameFormat("db-scheduler-%d")
        .setDaemon(true)
        .build(),
    new ThreadPoolExecutor.AbortPolicy()  // ← 问题所在
)
```

### 1.3 风险场景

```
时刻 T: 数据库线程池满载 (100 线程 + 5000 队列)
┌─────────────────────────────────────────────────────────┐
│ 客户端请求 → Netty Worker → submit(databaseScheduler)   │
│                     ↓                                    │
│                     队列满 → AbortPolicy 抛异常           │
│                     ↓                                    │
│              Reactor subscribe 捕获异常                   │
│                     ↓                                    │
│          onError 回调执行，但 Worker 可能已受影响         │
└─────────────────────────────────────────────────────────┘
```

---

## 2. 设计目标

### 2.1 核心目标

1. **防止 IO 线程阻塞**：线程池过载时不阻塞 Netty Worker 线程
2. **优雅降级**：系统过载时返回可预测的响应，而非异常
3. **自然背压**：通过 CallerRuns 策略形成自我保护机制

### 2.2 非目标

- 不解决根本的容量问题（需要扩容或优化数据库性能）
- 不替代客户端限流（客户端仍需实现合理的发送速率）

---

## 3. 架构设计

### 3.1 多层防御体系

```
┌─────────────────────────────────────────────────────────────┐
│                      客户端请求                              │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│  第 1 层：Netty 层                                              │
│  - 连接数限制 (ChannelMaintainer)                             │
│  - READ_IDLE 超时断开 (10s)                                   │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│  第 2 层：DispatcherHandler 入口限流 (新增)                    │
│  - Semaphore 限制并发处理数 (默认 50)                         │
│  - 超限快速失败：返回 CODE_BUSY                              │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│  第 3 层：数据库线程池                                         │
│  - 有界队列 (5000)                                            │
│  - CallerRunsPolicy (满载时反压) ← 修改                      │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│  第 4 层：数据库连接池                                         │
│  - HikariCP maximum-pool-size (50)                            │
└─────────────────────────────────────────────────────────────┘
```

### 3.2 响应码扩展

在 `RespondCode` 枚举中添加：

```protobuf
CODE_BUSY = 8;  // 服务器繁忙，请稍后重试
```

### 3.3 流程图

```
                              客户端请求
                                  ↓
                    ┌─────────────────────────┐
                    │ Semaphore.tryAcquire()  │
                    └───────────┬─────────────┘
                                │
                 ┌──────────────┼──────────────┐
                 │ NO           │ YES           │
                 ↓              ↓               │
        ┌────────────────┐   ┌──────────────────┴────────────┐
        │ 快速失败        │   │ 进入正常处理流程              │
        │ 返回 CODE_BUSY  │   │  ↓                            │
        └────────────────┘   │ submit(databaseScheduler)      │
                             │  ↓                            │
                             │  ├─ 队列有空闲 → 执行          │
                             │  └─ 队列满 → CallerRuns 执行   │
                             └────────────────────────────────┘
```

---

## 4. 详细设计

### 4.1 修改文件清单

| 文件 | 修改内容 | 说明 |
|------|---------|------|
| `src/main/proto/message.proto` | 添加 `CODE_BUSY = 8` | 新增响应码 |
| `src/main/java/cn/tnar/parking/model/RespondProto.java` | 添加 `busy()` 方法 | 工厂方法 |
| `src/main/java/cn/tnar/parking/netty/handler/DispatcherHandler.java` | 添加 Semaphore 限流 | 入口限流 |
| `src/main/java/cn/tnar/parking/config/SchedulerConfig.java` | 修改拒绝策略 | CallerRunsPolicy |
| `src/main/resources/application.yml` | 添加 `inbound-limiter` 配置 | 可配置限流阈值 |
| `src/main/java/cn/tnar/parking/config/NettyProperties.java` | 创建或修改 | 配置属性类 |

### 4.2 关键代码变更

#### 4.2.1 CODE_BUSY 响应码

```protobuf
enum RespondCode{
  CODE_NOT_USED      = 0;
  CODE_SUCCESS       = 1;
  CODE_FAILURE       = 2;
  CODE_DUPLICATE     = 3;
  CODE_MISMATCH      = 4;
  CODE_NOT_SUPPORTED = 5;
  CODE_OFFLINE       = 6;
  CODE_TIMEOUT       = 7;
  CODE_BUSY          = 8;  // 新增
}
```

#### 4.2.2 Semaphore 限流

```java
@Slf4j
@Component
@ChannelHandler.Sharable
public class DispatcherHandler extends SimpleChannelInboundHandler<Transmission> {
    
    private final HandlerRegistry handlerRegistry;
    private final Scheduler databaseScheduler;
    private final Semaphore inboundLimiter;  // 新增

    public DispatcherHandler(
        HandlerRegistry handlerRegistry,
        SchedulerConfig schedulerConfig,
        @Autowired(required = false) NettyProperties nettyProperties) {
        this.handlerRegistry = handlerRegistry;
        this.databaseScheduler = schedulerConfig.databaseScheduler(
            schedulerConfig.databaseSchedulerProperties());
        this.inboundLimiter = new Semaphore(
            nettyProperties != null ? nettyProperties.getInboundLimiter() : 50);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Transmission msg) {
        // 快速失败限流
        if (!inboundLimiter.tryAcquire()) {
            String parkCode = ctx.channel().attr(ChannelMaintainer.PARK_CODE_ATTR).get();
            log.warn("[LIMITER] 并发数超限，快速失败，parkCode={}, type={}", parkCode, msg.getType());
            Transmission busyResponse = Transmission.newBuilder()
                .setID(msg.getID())
                .setType(Transmission.MessageType.TYPE_RESPOND)
                .setMessage(RespondProto.busy().toByteString())
                .build();
            ctx.channel().writeAndFlush(busyResponse);
            return;
        }

        try {
            // 正常处理逻辑...
        } finally {
            inboundLimiter.release();  // 确保释放
        }
    }
}
```

#### 4.2.3 线程池拒绝策略

```java
@Bean
public Scheduler databaseScheduler(SchedulerProperties properties) {
    return Schedulers.fromExecutor(
        new ThreadPoolExecutor(
            properties.getCoreThreads(),
            properties.getMaxThreads(),
            60L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(properties.getQueueCapacity()),
            new ThreadFactoryBuilder()
                .setNameFormat("db-scheduler-%d")
                .setDaemon(true)
                .build(),
            new ThreadPoolExecutor.CallerRunsPolicy()  // 修改
        )
    );
}
```

### 4.3 配置项

```yaml
netty:
  tcp:
    port: 8889
    auth-timeout: 10
    heartbeat-interval: 90
    backlog: 128
    inbound-limiter: 50  # 新增：并发处理上限
```

---

## 5. 测试计划

### 5.1 单元测试

```java
class DispatcherHandlerTest {
    @Test
    void testHandle_WithinLimit()  // 限流内正常处理
    @Test
    void testHandle_ExceedLimit_returnsBusy()  // 超限返回繁忙
}
```

### 5.2 集成测试

1. 启动服务器，设置 `inbound-limiter: 3`
2. 并发发送 5 个请求
3. 验证至少 2 个请求返回 `CODE_BUSY`

### 5.3 压力测试

使用 JMeter 或自定义脚本：
- 并发连接数：100
- 发送速率：1000 req/s
- 观察服务端响应时间分布和 `CODE_BUSY` 比例

---

## 6. 监控告警

### 6.1 新增指标

```java
// 在 MetricsAspect 中添加
meterRegistry.counter("parking.netty.limiter.rejected.total", 
    "park_code", parkCode).increment();
```

### 6.2 告警规则

```yaml
# prometheus/alert_rules.yml
groups:
  - name: parking-netty
    rules:
      - alert: HighLimiterRejectionRate
        expr: rate(parking_netty_limiter_rejected_total[5m]) > 10
        annotations:
          summary: "限流拒绝率过高"
```

---

## 7. 部署注意事项

1. **配置调优**：根据实际并发量调整 `inbound-limiter` 值
2. **灰度发布**：先在测试环境验证限流效果
3. **监控观察**：部署后密切关注 `CODE_BUSY` 响应比例

---

## 8. 验收标准

- [ ] 并发请求超过阈值时，快速返回 `CODE_BUSY`
- [ ] 线程池满载时不抛异常，由调用者线程执行
- [ ] 所有现有单元测试通过
- [ ] 代码风格检查通过
- [ ] 监控指标正常收集
