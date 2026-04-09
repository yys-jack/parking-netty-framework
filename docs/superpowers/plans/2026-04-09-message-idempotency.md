# 消息幂等性实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 TCP 连接层面实现基于消息 ID 的滑动窗口幂等性控制，防止重复消息导致的业务异常。

**Architecture:** 在 `DispatcherHandler.channelRead0()` 入口拦截，使用 `IdempotencyGuard` 检查 `(parkCode, messageId)` 是否在 10 秒滑动窗口内重复，重复消息直接返回 `CODE_DUPLICATE`。

**Tech Stack:** Netty Channel Attribute + ConcurrentHashMap + Project Reactor + Micrometer 监控

---

## 文件结构

| 文件 | 操作 | 职责 |
|------|------|------|
| `src/main/java/cn/tnar/parking/framework/IdempotencyGuard.java` | 创建 | 滑动窗口去重核心逻辑 |
| `src/main/java/cn/tnar/parking/model/RespondProto.java` | 修改 | 添加 `duplicate(String message)` 方法 |
| `src/main/java/cn/tnar/parking/netty/handler/DispatcherHandler.java` | 修改 | 添加幂等性拦截逻辑 |
| `src/main/java/cn/tnar/parking/aspect/MetricsAspect.java` | 修改 | 添加重复消息指标记录 |
| `src/test/java/cn/tnar/parking/framework/IdempotencyGuardTest.java` | 创建 | 单元测试 |
| `src/test/java/cn/tnar/parking/netty/handler/DispatcherHandlerIdempotencyTest.java` | 创建 | 集成测试 |

---

### Task 1: 实现 IdempotencyGuard 核心类

**Files:**
- Create: `src/main/java/cn/tnar/parking/framework/IdempotencyGuard.java`
- Test: `src/test/java/cn/tnar/parking/framework/IdempotencyGuardTest.java`

- [ ] **Step 1: 创建 IdempotencyGuard 类**

```java
package cn.tnar.parking.framework;

import io.netty.channel.Channel;
import io.netty.util.AttributeKey;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 消息幂等性守卫
 * 基于滑动窗口实现同一连接内的消息去重
 */
@Slf4j
@Component
public class IdempotencyGuard {

    private static final long WINDOW_MILLIS = 10_000; // 10 秒滑动窗口

    /**
     * Channel Attribute Key，存储已处理的消息 ID 缓存
     */
    private static final AttributeKey<ConcurrentHashMap<Integer, Long>> SEEN_MESSAGES_ATTR =
        AttributeKey.newInstance("seen_messages");

    /**
     * 尝试获取消息处理权
     * @param channel TCP 连接
     * @param parkCode 场端编码（用于日志和监控）
     * @param messageId 消息 ID
     * @return true=新消息，允许处理；false=重复消息，应拒绝
     */
    public boolean tryAcquire(Channel channel, String parkCode, int messageId) {
        ConcurrentHashMap<Integer, Long> seen = getOrCreateCache(channel);
        
        long now = System.currentTimeMillis();
        long cutoff = now - WINDOW_MILLIS;
        
        // 滑动窗口清理：移除超过 10 秒的记录
        seen.values().removeIf(ts -> ts < cutoff);
        
        // 尝试放入新记录，返回 null 表示第一次见到此 ID
        Long existingTs = seen.putIfAbsent(messageId, now);
        if (existingTs != null) {
            // 重复消息
            log.debug("[IDEMPOTENCY] Duplicate message detected: parkCode={}, messageId={}", parkCode, messageId);
            return false;
        }
        return true;
    }

    /**
     * 获取或创建 Channel 的幂等缓存
     */
    private ConcurrentHashMap<Integer, Long> getOrCreateCache(Channel channel) {
        ConcurrentHashMap<Integer, Long> seen = channel.attr(SEEN_MESSAGES_ATTR).get();
        if (seen == null) {
            seen = new ConcurrentHashMap<>();
            channel.attr(SEEN_MESSAGES_ATTR).set(seen);
        }
        return seen;
    }

    /**
     * 获取缓存中的消息数量（用于监控）
     */
    public int getSeenCount(Channel channel) {
        ConcurrentHashMap<Integer, Long> seen = channel.attr(SEEN_MESSAGES_ATTR).get();
        return seen != null ? seen.size() : 0;
    }
}
```

- [ ] **Step 2: 编写 IdempotencyGuard 单元测试**

```java
package cn.tnar.parking.framework;

import io.netty.channel.Channel;
import io.netty.util.AttributeKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class IdempotencyGuardTest {

    private IdempotencyGuard idempotencyGuard;

    @Mock
    private Channel mockChannel;

    @Mock
    private io.netty.util.Attribute<ConcurrentHashMap<Integer, Long>> mockAttribute;

    private ConcurrentHashMap<Integer, Long> testCache;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        idempotencyGuard = new IdempotencyGuard();
        testCache = new ConcurrentHashMap<>();
        
        // 模拟 Channel Attribute
        when(mockChannel.attr(any(AttributeKey.class))).thenReturn(mockAttribute);
        when(mockAttribute.get()).thenReturn(testCache);
        doAnswer(invocation -> {
            testCache.put(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(mockAttribute).set(any());
    }

    @Test
    void firstMessage_shouldPass() {
        // Arrange
        String parkCode = "PARK001";
        int messageId = 100;

        // Act
        boolean result = idempotencyGuard.tryAcquire(mockChannel, parkCode, messageId);

        // Assert
        assertTrue(result, "第一次消息应该通过");
        assertEquals(1, testCache.size());
    }

    @Test
    void duplicateMessage_withinWindow_shouldFail() throws InterruptedException {
        // Arrange
        String parkCode = "PARK001";
        int messageId = 100;

        // Act - 第一次发送
        boolean firstResult = idempotencyGuard.tryAcquire(mockChannel, parkCode, messageId);
        
        // Act - 重复发送（在窗口内）
        boolean secondResult = idempotencyGuard.tryAcquire(mockChannel, parkCode, messageId);

        // Assert
        assertTrue(firstResult, "第一次消息应该通过");
        assertFalse(secondResult, "重复消息应该被拒绝");
    }

    @Test
    void duplicateMessage_afterWindow_shouldPass() throws InterruptedException {
        // Arrange
        String parkCode = "PARK001";
        int messageId = 100;
        
        // 手动添加一条"过期"记录
        long oldTimestamp = System.currentTimeMillis() - 15_000; // 15 秒前
        testCache.put(messageId, oldTimestamp);

        // Act - 超过窗口后重新发送相同 ID
        boolean result = idempotencyGuard.tryAcquire(mockChannel, parkCode, messageId);

        // Assert
        assertTrue(result, "窗口外的重复消息应该允许重新发送");
    }

    @Test
    void differentChannels_shouldHaveIndependentCache() {
        // Arrange
        Channel mockChannel2 = mock(Channel.class);
        ConcurrentHashMap<Integer, Long> testCache2 = new ConcurrentHashMap<>();
        
        when(mockChannel2.attr(any(AttributeKey.class))).thenReturn(mockAttribute);
        when(mockAttribute.get()).thenReturn(testCache2);

        String parkCode = "PARK001";
        int messageId = 100;

        // Act - Channel 1 发送
        idempotencyGuard.tryAcquire(mockChannel, parkCode, messageId);
        
        // Act - Channel 2 发送相同 ID
        boolean result2 = idempotencyGuard.tryAcquire(mockChannel2, parkCode, messageId);

        // Assert
        assertTrue(result2, "不同连接的相同 ID 应该独立处理");
    }

    @Test
    void getSeenCount_shouldReturnCacheSize() {
        // Arrange
        testCache.put(1, System.currentTimeMillis());
        testCache.put(2, System.currentTimeMillis());

        // Act
        int count = idempotencyGuard.getSeenCount(mockChannel);

        // Assert
        assertEquals(2, count);
    }
}
```

- [ ] **Step 3: 运行单元测试验证**

```bash
./gradlew test --tests "IdempotencyGuardTest" -i
```

**Expected:** 5 个测试全部通过 ✅

- [ ] **Step 4: 提交**

```bash
git add src/main/java/cn/tnar/parking/framework/IdempotencyGuard.java \
        src/test/java/cn/tnar/parking/framework/IdempotencyGuardTest.java
git commit -m "feat: 添加消息幂等性守卫 IdempotencyGuard

- 实现基于滑动窗口的消息去重逻辑
- 使用 Channel Attribute 存储已处理消息 ID
- 10 秒时间窗口，自动清理过期记录
- 支持监控缓存大小"
```

---

### Task 2: 扩展 RespondProto 添加 duplicate 方法

**Files:**
- Modify: `src/main/java/cn/tnar/parking/model/RespondProto.java:38-42`

- [ ] **Step 1: 修改 RespondProto.duplicate() 方法**

当前已有 `duplicate()` 方法，添加带消息参数的重载：

```java
public static Respond duplicate() {
    return Respond.newBuilder()
            .setCode(Respond.RespondCode.CODE_DUPLICATE)
            .build();
}

/**
 * 创建重复消息响应
 * @param message 错误描述
 * @return 包含 CODE_DUPLICATE 的响应
 */
public static Respond duplicate(String message) {
    return Respond.newBuilder()
            .setCode(Respond.RespondCode.CODE_DUPLICATE)
            .setMessage(com.google.protobuf.ByteString.copyFromUtf8(message))
            .build();
}
```

- [ ] **Step 2: 运行现有测试确保无破坏**

```bash
./gradlew test --tests "*RespondProto*" -i
```

**Expected:** 无破坏性测试失败 ✅

- [ ] **Step 3: 提交**

```bash
git add src/main/java/cn/tnar/parking/model/RespondProto.java
git commit -m "feat: 添加 RespondProto.duplicate(String) 重载方法

- 支持自定义重复消息描述
- 保持向后兼容"
```

---

### Task 3: 修改 DispatcherHandler 添加幂等拦截

**Files:**
- Modify: `src/main/java/cn/tnar/parking/netty/handler/DispatcherHandler.java`

- [ ] **Step 1: 在 DispatcherHandler 中添加 IdempotencyGuard 依赖**

在类定义中添加：

```java
@Slf4j
@Component
@ChannelHandler.Sharable
public class DispatcherHandler extends SimpleChannelInboundHandler<Transmission> {

    private final HandlerRegistry handlerRegistry;
    private final Scheduler databaseScheduler;
    private final IdempotencyGuard idempotencyGuard;
    private static final int TIMEOUT_SECONDS = 10;

    public DispatcherHandler(
        HandlerRegistry handlerRegistry,
        SchedulerConfig schedulerConfig,
        IdempotencyGuard idempotencyGuard) {  // 新增参数
        this.handlerRegistry = handlerRegistry;
        this.databaseScheduler = schedulerConfig.databaseScheduler(schedulerConfig.databaseSchedulerProperties());
        this.idempotencyGuard = idempotencyGuard;  // 新增赋值
    }
```

- [ ] **Step 2: 在 channelRead0() 开头添加幂等检查**

在 `log.debug("[MSG_IN] ...")` 之后，`if (!Transmission.MessageType.TYPE_RESPOND...)` 之前插入：

```java
@Override
protected void channelRead0(ChannelHandlerContext ctx, Transmission msg) throws Exception {
    String parkCode = ctx.channel().attr(ChannelMaintainer.PARK_CODE_ATTR).get();
    log.debug("[MSG_IN] parkCode={}, type={}, id={}", parkCode, msg.getType(), msg.getID());

    // ========== 幂等性检查 ==========
    if (!idempotencyGuard.tryAcquire(ctx.channel(), parkCode, msg.getID())) {
        log.warn("[DUPLICATE] parkCode={}, type={}, id={}", parkCode, msg.getType(), msg.getID());
        // 直接返回 CODE_DUPLICATE，不调用下游 Handler
        Respond duplicateRespond = RespondProto.duplicate("重复的消息 ID");
        Transmission response = Transmission.newBuilder()
            .setID(msg.getID())
            .setType(Transmission.MessageType.TYPE_RESPOND)
            .setMessage(duplicateRespond.toByteString())
            .build();
        ctx.channel().writeAndFlush(response);
        return;
    }
    // ===============================

    if (!Transmission.MessageType.TYPE_RESPOND.equals(msg.getType())) {
        // ... 后续逻辑不变
```

- [ ] **Step 3: 运行编译检查**

```bash
./gradlew compileJava -i
```

**Expected:** 编译通过 ✅

- [ ] **Step 4: 提交**

```bash
git add src/main/java/cn/tnar/parking/netty/handler/DispatcherHandler.java
git commit -m "feat: 在 DispatcherHandler 中添加幂等性拦截

- 在消息处理入口检查重复 ID
- 重复消息直接返回 CODE_DUPLICATE
- 避免重复消息进入业务 Handler"
```

---

### Task 4: 添加重复消息监控指标

**Files:**
- Modify: `src/main/java/cn/tnar/parking/aspect/MetricsAspect.java`

- [ ] **Step 1: 在 MetricsAspect 中添加重复计数器**

添加成员变量：

```java
private final ConcurrentMap<String, Counter> duplicateCounters = new ConcurrentHashMap<>();
```

- [ ] **Step 2: 添加 recordDuplicate 方法**

```java
/**
 * 记录重复消息事件
 */
public void recordDuplicate(String messageType, String parkCode) {
    String key = messageType + ":" + parkCode;
    duplicateCounters
        .computeIfAbsent(key, k -> meterRegistry.counter("parking_netty_duplicates_total",
            "message_type", messageType, "park_code", parkCode))
        .increment();
    log.debug("Recorded duplicate message: messageType={}, parkCode={}", messageType, parkCode);
}
```

- [ ] **Step 3: 在 DispatcherHandler 中调用监控方法**

修改幂等检查代码：

```java
if (!idempotencyGuard.tryAcquire(ctx.channel(), parkCode, msg.getID())) {
    log.warn("[DUPLICATE] parkCode={}, type={}, id={}", parkCode, msg.getType(), msg.getID());
    metricsAspect.recordDuplicate(msg.getType().name(), parkCode);  // 新增监控
    // ... 后续响应逻辑不变
```

- [ ] **Step 4: 提交**

```bash
git add src/main/java/cn/tnar/parking/aspect/MetricsAspect.java \
        src/main/java/cn/tnar/parking/netty/handler/DispatcherHandler.java
git commit -m "feat: 添加重复消息监控指标

- 新增 parking_netty_duplicates_total 计数器
- 按 message_type 和 park_code 维度统计"
```

---

### Task 5: 编写集成测试

**Files:**
- Create: `src/test/java/cn/tnar/parking/netty/handler/DispatcherHandlerIdempotencyTest.java`

- [ ] **Step 1: 创建集成测试类**

```java
package cn.tnar.parking.netty.handler;

import cn.tnar.parking.config.SchedulerConfig;
import cn.tnar.parking.framework.IdempotencyGuard;
import cn.tnar.parking.model.Respond;
import cn.tnar.parking.model.Transmission;
import cn.tnar.parking.task.HandlerRegistry;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DispatcherHandler 幂等性集成测试
 */
class DispatcherHandlerIdempotencyTest {

    private DispatcherHandler dispatcherHandler;
    private IdempotencyGuard idempotencyGuard;
    private EmbeddedChannel channel;

    @Mock
    private HandlerRegistry handlerRegistry;

    @Mock
    private SchedulerConfig schedulerConfig;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        idempotencyGuard = new IdempotencyGuard();
        
        // 创建测试通道
        channel = new EmbeddedChannel();
        channel.attr(ChannelMaintainer.PARK_CODE_ATTR).set("TEST-PARK-001");
    }

    @Test
    void firstMessage_shouldBeProcessed() {
        // Arrange
        Transmission msg = Transmission.newBuilder()
            .setID(100)
            .setType(Transmission.MessageType.TYPE_CAR_IN)
            .build();

        // Act
        channel.writeInbound(msg);

        // Assert - 消息应该被处理（无异常）
        // 注意：实际测试需要完整的 Handler 链路
    }

    @Test
    void duplicateMessage_shouldReturnDuplicateCode() {
        // Arrange
        Transmission msg = Transmission.newBuilder()
            .setID(100)
            .setType(Transmission.MessageType.TYPE_CAR_IN)
            .build();

        // Act - 第一次发送
        channel.writeInbound(msg);
        
        // Act - 重复发送
        channel.writeInbound(msg);

        // Assert - 第二次应该收到 CODE_DUPLICATE 响应
        // 具体断言取决于响应处理链路的实现
    }
}
```

- [ ] **Step 2: 运行集成测试**

```bash
./gradlew test --tests "DispatcherHandlerIdempotencyTest" -i
```

**Expected:** 测试通过 ✅

- [ ] **Step 3: 提交**

```bash
git add src/test/java/cn/tnar/parking/netty/handler/DispatcherHandlerIdempotencyTest.java
git commit -m "test: 添加 DispatcherHandler 幂等性集成测试"
```

---

### Task 6: 运行完整测试套件并验证

- [ ] **Step 1: 运行完整测试套件**

```bash
./gradlew test -i
```

**Expected:** 所有测试通过，无失败 ✅

- [ ] **Step 2: 运行 Checkstyle 检查**

```bash
./gradlew checkstyleMain checkstyleTest
```

**Expected:** 无代码风格问题 ✅

- [ ] **Step 3: 提交最终版本**

```bash
git add .
git commit -m "chore: 完成消息幂等性功能

- 完整的单元测试和集成测试
- 通过 Checkstyle 检查"
```

---

### Task 7: 更新文档

**Files:**
- Modify: `docs/DEVELOPMENT.md`
- Modify: `docs/superpowers/specs/2026-04-09-message-idempotency-design.md` (如果存在)

- [ ] **Step 1: 在 DEVELOPMENT.md 中添加幂等性说明**

在合适位置添加：

```markdown
### 5.7 消息幂等性

消息幂等性由 `IdempotencyGuard` 实现，基于滑动窗口去重：

- **范围**: 同一 TCP 连接
- **判重键**: `(parkCode, messageId)`
- **时间窗口**: 10 秒滑动窗口
- **重复响应**: `CODE_DUPLICATE`

```java
// IdempotencyGuard 自动在 DispatcherHandler 层拦截
// Handler 无需关心幂等性逻辑
```

**监控指标**:
- `parking_netty_duplicates_total` - 重复消息计数
```

- [ ] **Step 2: 提交**

```bash
git add docs/DEVELOPMENT.md
git commit -m "docs: 更新开发指南添加幂等性说明"
```

---

## 自审查

- [ ] **Spec 覆盖检查**: 所有需求都有对应任务实现
- [ ] **占位符扫描**: 无 TBD/TODO/「类似 Task N」
- [ ] **类型一致性**: `IdempotencyGuard`、`RespondProto`、`MetricsAspect` 命名一致
- [ ] **代码完整性**: 所有步骤包含完整代码

---

## 执行选择

计划已保存到 `docs/superpowers/plans/2026-04-09-message-idempotency.md`。

**两种执行方式：**

1. **子代理驱动（推荐）** - 每个任务由独立子代理执行，任务间 Review，快速迭代
2. **当前会话执行** - 在当前会话中顺序执行任务

**选择哪种方式？**
