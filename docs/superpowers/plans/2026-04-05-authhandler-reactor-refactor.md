# AuthHandler 响应式重构实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 重构 AuthHandler.java 消除嵌套 subscribe 反模式，使用扁平化 Reactor 链式调用

**Architecture:** 将当前嵌套 subscribe 的回调式代码改写为单一 Reactor 链式调用，使用 flatMap、switchIfEmpty、doOnNext、onErrorResume 等操作符替代多层嵌套，保持认证流程、指标收集、Channel 管理逻辑完全一致

**Tech Stack:** Project Reactor (Mono), Netty, Spring Boot, Micrometer

---

### Task 1: 分析当前 AuthHandler 代码结构

**Files:**
- Read: `src/main/java/cn/tnar/parking/netty/handler/AuthHandler.java`

- [ ] **Step 1: 识别嵌套 subscribe 位置**

当前代码有两层嵌套 subscribe：
```java
// 第 61-65 行：第一层 subscribe
authRepository.validate(...)
    .subscribe(
        isValid -> handleValid(...),  // 嵌套第二个 subscribe
        error -> handleError(...)
    );

// 第 70-109 行：handleValid 内部第二层 subscribe
authRepository.findBySerial(...)
    .subscribe(
        authInfo -> { ... },
        error -> { ... }
    );
```

- [ ] **Step 2: 提取独立方法签名**

需要抽取的私有方法：
```java
private void removeAuthHandler(ChannelPipeline pipeline)  // 已存在
private void handleError(ChannelHandlerContext ctx, Channel channel, Throwable error, long authStart, String serial)  // 已存在
```

重构后需要的新方法：
```java
private Mono<AuthInfo> authenticateAndFetchInfo(String serial, String secret)
private void completeAuthentication(ChannelHandlerContext ctx, Transmission msg, AuthInfo authInfo, long authStart, String serial)
private void handleAuthError(ChannelHandlerContext ctx, Throwable error, long authStart, String serial)
```

- [ ] **Step 3: 确认测试覆盖**

当前测试文件：
- `src/test/java/cn/tnar/parking/netty/handler/AuthHandlerMetricsTest.java` - 测试 MetricsAspect

需要添加的测试：
- `src/test/java/cn/tnar/parking/netty/handler/AuthHandlerTest.java` - 测试认证流程

---

### Task 2: 创建 AuthHandler 单元测试

**Files:**
- Create: `src/test/java/cn/tnar/parking/netty/handler/AuthHandlerTest.java`
- Modify: `src/main/java/cn/tnar/parking/netty/handler/AuthHandler.java` (使方法可测试)

- [ ] **Step 1: 编写测试类骨架**

```java
package cn.tnar.parking.netty.handler;

import cn.tnar.parking.aspect.MetricsAspect;
import cn.tnar.parking.model.Authenticate;
import cn.tnar.parking.model.Transmission;
import cn.tnar.parking.netty.ChannelMaintainer;
import cn.tnar.parking.repository.auth.AuthInfo;
import cn.tnar.parking.repository.auth.ReactiveAuthRepository;
import io.netty.channel.embedded.EmbeddedChannel;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class AuthHandlerTest {

    @Mock
    private ReactiveAuthRepository authRepository;

    private SimpleMeterRegistry registry;
    private MetricsAspect metricsAspect;
    private AuthHandler authHandler;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        registry = new SimpleMeterRegistry();
        metricsAspect = new MetricsAspect(registry);
        authHandler = new AuthHandler(authRepository, metricsAspect);
    }

    @Test
    void testAuthenticationSuccess() {
        // TODO: 实现认证成功测试
    }

    @Test
    void testAuthenticationFailure_InvalidCredentials() {
        // TODO: 实现认证失败测试
    }

    @Test
    void testAuthenticationFailure_SerialNotFound() {
        // TODO: 实现序列号不存在测试
    }
}
```

- [ ] **Step 2: 运行测试验证骨架编译通过**

```bash
./gradlew test --tests "AuthHandlerTest" --info
```

期望结果：编译通过，测试方法为空或跳过

---

### Task 3: 实现认证成功测试

**Files:**
- Modify: `src/test/java/cn/tnar/parking/netty/handler/AuthHandlerTest.java`

- [ ] **Step 1: 编写认证成功测试**

```java
@Test
void testAuthenticationSuccess() {
    // 准备测试数据
    String serial = "TEST-001";
    String secret = "test-secret";
    String parkCode = "PARK001";
    AuthInfo authInfo = new AuthInfo(serial, parkCode, secret);

    // 模拟 Repository 行为
    when(authRepository.validate(serial, secret)).thenReturn(Mono.just(true));
    when(authRepository.findBySerial(serial)).thenReturn(Mono.just(authInfo));

    // 构建认证消息
    Authenticate authenticate = Authenticate.newBuilder()
        .setSerial(serial)
        .setSecret(secret)
        .build();
    Transmission transmission = Transmission.newBuilder()
        .setType(Transmission.MessageType.TYPE_AUTHENTICATE)
        .setMessage(authenticate.toByteString())
        .setID(12345)
        .build();

    // 创建 EmbeddedChannel 并设置 PARK_CODE_ATTR
    EmbeddedChannel channel = new EmbeddedChannel();
    channel.attr(ChannelMaintainer.PARK_CODE_ATTR).set(parkCode);

    // 创建 ChannelHandlerContext
    EmbeddedChannel ctx = new EmbeddedChannel(authHandler);
    ctx.writeInbound(transmission);

    // 验证：ChannelMaintainer 中添加了 channel
    // 验证：认证成功指标被记录
    assertEquals(1.0, registry.counter("parking_netty_auth_success_total", "park_code", parkCode).count());
    verify(authRepository, times(1)).validate(serial, secret);
    verify(authRepository, times(1)).findBySerial(serial);

    ctx.close();
}
```

- [ ] **Step 2: 运行测试验证失败**

```bash
./gradlew test --tests "AuthHandlerTest.testAuthenticationSuccess" -i
```

期望结果：测试失败（因为 AuthHandler 尚未重构，行为可能不符合预期）

---

### Task 4: 重构 AuthHandler 主体逻辑

**Files:**
- Modify: `src/main/java/cn/tnar/parking/netty/handler/AuthHandler.java`

- [ ] **Step 1: 重构 channelRead0 方法为扁平化链式调用**

```java
@Override
protected void channelRead0(ChannelHandlerContext ctx, Transmission msg) {
    Channel channel = ctx.channel();
    log.info("AuthHandler received message type: {}", msg.getType());

    if (msg.getType() != Transmission.MessageType.TYPE_AUTHENTICATE) {
        log.warn("Non-authenticate message received, closing connection");
        channel.close();
        return;
    }

    Authenticate authenticate;
    try {
        authenticate = Authenticate.parseFrom(msg.getMessage());
    } catch (InvalidProtocolBufferException e) {
        log.error("Failed to parse authenticate message", e);
        channel.close();
        return;
    }

    String serial = authenticate.getSerial();
    long authStart = System.nanoTime();

    // 扁平化链式调用替代嵌套 subscribe
    authRepository.validate(serial, secret)
        .flatMap(isValid -> {
            if (!isValid) {
                return Mono.error(new AuthenticationException("invalid credentials"));
            }
            return authRepository.findBySerial(serial)
                .switchIfEmpty(Mono.error(new AuthenticationException("serial not found")));
        })
        .doOnNext(authInfo -> completeAuthentication(ctx, msg, authInfo, authStart, serial))
        .onErrorResume(error -> {
            handleAuthError(ctx, error, authStart, serial);
            return Mono.empty();
        })
        .subscribe();
}
```

- [ ] **Step 2: 添加 completeAuthentication 方法**

```java
private void completeAuthentication(ChannelHandlerContext ctx, Transmission msg,
                                    AuthInfo authInfo, long authStart, String serial) {
    long duration = System.nanoTime() - authStart;
    double durationSeconds = duration / 1_000_000_000.0;

    String parkCode = authInfo.getParkCode();
    metricsAspect.recordAuthSuccess(parkCode, durationSeconds);
    ChannelMaintainer.add(parkCode, ctx.channel());
    ctx.channel().attr(ChannelMaintainer.PARK_CODE_ATTR).setIfAbsent(parkCode);

    Respond resp = RespondProto.success();
    Transmission ret = Transmission.newBuilder()
        .setType(Transmission.MessageType.TYPE_RESPOND)
        .setID(msg.getID())
        .setMessage(resp.toByteString())
        .build();

    ctx.channel().writeAndFlush(ret).addListener(future -> {
        if (future.isSuccess()) {
            log.info("parkCode: {} auth success", parkCode);
        } else {
            log.error("parkCode: {} auth success but response failed", parkCode);
        }
        removeAuthHandler(ctx.pipeline());
    });
}
```

- [ ] **Step 3: 添加 handleAuthError 方法**

```java
private void handleAuthError(ChannelHandlerContext ctx, Throwable error,
                             long authStart, String serial) {
    long duration = System.nanoTime() - authStart;
    double durationSeconds = duration / 1_000_000_000.0;
    String reason = error.getMessage() != null ? error.getMessage() : "unknown error";
    
    log.error("Authentication error: " + error.getMessage());
    metricsAspect.recordAuthFailure(serial, "error: " + reason, durationSeconds);
    ctx.channel().close();
}
```

- [ ] **Step 4: 删除旧的 handleValid 和 handleError 方法**

删除原有的 `handleValid()` 和 `handleError()` 方法，因为逻辑已整合到新方法中

---

### Task 5: 实现认证失败测试

**Files:**
- Modify: `src/test/java/cn/tnar/parking/netty/handler/AuthHandlerTest.java`

- [ ] **Step 1: 实现认证失败（凭证无效）测试**

```java
@Test
void testAuthenticationFailure_InvalidCredentials() {
    String serial = "TEST-001";
    String secret = "wrong-secret";

    when(authRepository.validate(serial, secret)).thenReturn(Mono.just(false));

    Authenticate authenticate = Authenticate.newBuilder()
        .setSerial(serial)
        .setSecret(secret)
        .build();
    Transmission transmission = Transmission.newBuilder()
        .setType(Transmission.MessageType.TYPE_AUTHENTICATE)
        .setMessage(authenticate.toByteString())
        .setID(12345)
        .build();

    EmbeddedChannel ctx = new EmbeddedChannel(authHandler);
    ctx.writeInbound(transmission);

    // 验证：认证失败指标被记录
    assertEquals(1.0, registry.counter("parking_netty_auth_failure_total",
        "park_code", serial, "reason", "error: invalid credentials").count());
    verify(authRepository, times(1)).validate(serial, secret);
    verify(authRepository, never()).findBySerial(anyString());

    ctx.close();
}
```

- [ ] **Step 2: 实现序列号不存在测试**

```java
@Test
void testAuthenticationFailure_SerialNotFound() {
    String serial = "NOT-EXISTS";
    String secret = "test-secret";

    when(authRepository.validate(serial, secret)).thenReturn(Mono.just(true));
    when(authRepository.findBySerial(serial)).thenReturn(Mono.empty());

    Authenticate authenticate = Authenticate.newBuilder()
        .setSerial(serial)
        .setSecret(secret)
        .build();
    Transmission transmission = Transmission.newBuilder()
        .setType(Transmission.MessageType.TYPE_AUTHENTICATE)
        .setMessage(authenticate.toByteString())
        .setID(12345)
        .build();

    EmbeddedChannel ctx = new EmbeddedChannel(authHandler);
    ctx.writeInbound(transmission);

    // 验证：认证失败指标被记录（serial not found）
    assertEquals(1.0, registry.counter("parking_netty_auth_failure_total",
        "park_code", serial, "reason", "error: serial not found").count());

    ctx.close();
}
```

- [ ] **Step 3: 运行所有 AuthHandler 测试**

```bash
./gradlew test --tests "AuthHandlerTest" -i
```

期望结果：所有测试通过

---

### Task 6: 清理与验证

**Files:**
- Modify: `src/main/java/cn/tnar/parking/netty/handler/AuthHandler.java`
- Test: 运行完整测试套件

- [ ] **Step 1: 检查代码风格**

```bash
./gradlew checkstyleMain
```

期望结果：无 Checkstyle 违规

- [ ] **Step 2: 运行完整测试套件**

```bash
./gradlew test
```

期望结果：所有测试通过

- [ ] **Step 3: 验证代码行数减少**

重构前：137 行  
重构后目标：≤ 90 行

- [ ] **Step 4: Commit 重构成果**

```bash
git add src/main/java/cn/tnar/parking/netty/handler/AuthHandler.java
git add src/test/java/cn/tnar/parking/netty/handler/AuthHandlerTest.java
git commit -m "refactor: 重构 AuthHandler 消除嵌套 subscribe 反模式"
```

---

## 验收标准

1. **代码质量**:
   - 无嵌套 subscribe
   - 单一 subscribe 在链式调用末端
   - 代码行数从 137 降至 ≤ 90 行
   - Checkstyle 检查通过

2. **功能正确性**:
   - 所有原有测试通过
   - 新增 3 个 AuthHandler 单元测试通过
   - 认证成功、失败、序列号不存在场景均被覆盖

3. **可维护性**:
   - 错误处理集中化
   - 方法职责清晰
   - 符合 Reactor 编程范式
