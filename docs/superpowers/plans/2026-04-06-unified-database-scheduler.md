# Unified Database Scheduler Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 移除冗余的 `boundedElastic` 调度器，统一使用 `databaseScheduler` 管理所有数据库相关的线程调度。

**Architecture:** 
- 当前三层调度器（DispatcherHandler → AbstractHandler → Repository）中只有最内层生效
- 移除 AbstractHandler 和 Repository 中的 `boundedElastic` 调度器
- 由 DispatcherHandler 的 `databaseScheduler` 统一管理，该调度器可配置、可监控

**Tech Stack:** Project Reactor, Spring Boot 3.x, Schedulers

---

## 文件变更清单

| 文件 | 操作 | 说明 |
|------|------|------|
| `src/main/java/cn/tnar/parking/framework/AbstractHandler.java` | 修改 | 移除 `.subscribeOn(Schedulers.boundedElastic())` |
| `src/main/java/cn/tnar/parking/repository/auth/JdbcAuthRepository.java` | 修改 | 移除 `.subscribeOn(Schedulers.boundedElastic())` |
| `src/main/java/cn/tnar/parking/repository/auth/RedisAuthRepository.java` | 修改 | 移除 `.subscribeOn(Schedulers.boundedElastic())` |
| `src/main/java/cn/tnar/parking/netty/handler/DispatcherHandler.java` | 修改 | 更新日志描述，明确记录 `doHandle` 执行 |

---

### Task 1: 移除 AbstractHandler 中的 boundedElastic 调度器

**Files:**
- Modify: `src/main/java/cn/tnar/parking/framework/AbstractHandler.java:58-60`

- [ ] **Step 1: 阅读当前代码**

```java
// AbstractHandler.java 当前代码
@Override
public Mono<Respond> handle(Transmission transmission, String parkCode) {
    long start = System.nanoTime();

    return Mono.fromCallable(() -> doHandle(transmission, parkCode))
        .timeout(Duration.ofSeconds(TIMEOUT_SECONDS))
        // ... 日志和指标收集 ...
        .onErrorResume(e -> Mono.just(RespondProto.failure("处理超时：" + e.getMessage())))
        .subscribeOn(Schedulers.boundedElastic());  // ← 删除这行
}
```

- [ ] **Step 2: 移除 subscribeOn**

删除 `.subscribeOn(Schedulers.boundedElastic())`，让调度由上层 `DispatcherHandler` 统一管理。

- [ ] **Step 3: 移除多余的 import**

如果 `reactor.core.scheduler.Schedulers` 不再被使用，删除该 import。

- [ ] **Step 4: 编译验证**

```bash
./gradlew compileJava --quiet
```

Expected: 编译通过

- [ ] **Step 5: 运行现有测试**

```bash
./gradlew test --tests "*HandlerTest" --quiet
```

Expected: 所有 Handler 测试通过

- [ ] **Step 6: Commit**

```bash
git add src/main/java/cn/tnar/parking/framework/AbstractHandler.java
git commit -m "refactor: 移除 AbstractHandler 中的 boundedElastic 调度器"
```

---

### Task 2: 移除 JdbcAuthRepository 中的 boundedElastic 调度器

**Files:**
- Modify: `src/main/java/cn/tnar/parking/repository/auth/JdbcAuthRepository.java:25-35`

- [ ] **Step 1: 阅读当前代码**

```java
// JdbcAuthRepository.java 当前代码
@Override
public Mono<AuthInfo> findBySerial(String serial) {
    return Mono.fromCallable(() -> {
                // JDBC 查询逻辑
                List<AuthInfo> result = jdbcTemplate.query(...);
                return result.isEmpty() ? null : result.get(0);
            })
            .subscribeOn(Schedulers.boundedElastic());  // ← 删除这行
}
```

- [ ] **Step 2: 移除 subscribeOn**

删除 `.subscribeOn(Schedulers.boundedElastic())`，JDBC 操作将由 `databaseScheduler` 调度。

- [ ] **Step 3: 移除多余的 import**

如果 `reactor.core.scheduler.Schedulers` 不再被使用，删除该 import。

- [ ] **Step 4: 编译验证**

```bash
./gradlew compileJava --quiet
```

Expected: 编译通过

- [ ] **Step 5: 运行 Repository 测试**

```bash
./gradlew test --tests "*JdbcAuthRepositoryTest" --quiet
```

Expected: 测试通过

- [ ] **Step 6: Commit**

```bash
git add src/main/java/cn/tnar/parking/repository/auth/JdbcAuthRepository.java
git commit -m "refactor: 移除 JdbcAuthRepository 中的 boundedElastic 调度器"
```

---

### Task 3: 移除 RedisAuthRepository 中的 boundedElastic 调度器

**Files:**
- Modify: `src/main/java/cn/tnar/parking/repository/auth/RedisAuthRepository.java:85-93`

- [ ] **Step 1: 阅读当前代码**

```java
// RedisAuthRepository.java refreshCache() 方法
public Mono<Integer> refreshCache() {
    return Mono.fromCallable(() -> {
                String sql = "SELECT ... FROM auth_info WHERE enabled = 1";
                return jdbcTemplate.query(sql, ...);
            })
            .subscribeOn(Schedulers.boundedElastic())  // ← 删除这行
            .flatMap(authInfos -> {
                // Redis 保存逻辑
            });
}
```

- [ ] **Step 2: 移除 subscribeOn**

删除 `.subscribeOn(Schedulers.boundedElastic())`。

- [ ] **Step 3: 移除多余的 import**

如果 `reactor.core.scheduler.Schedulers` 不再被使用，删除该 import。

- [ ] **Step 4: 编译验证**

```bash
./gradlew compileJava --quiet
```

Expected: 编译通过

- [ ] **Step 5: 运行 Redis 相关测试**

```bash
./gradlew test --tests "*RedisAuthRepositoryTest" --quiet
```

Expected: 测试通过

- [ ] **Step 6: Commit**

```bash
git add src/main/java/cn/tnar/parking/repository/auth/RedisAuthRepository.java
git commit -m "refactor: 移除 RedisAuthRepository 中的 boundedElastic 调度器"
```

---

### Task 4: 更新 DispatcherHandler 日志描述

**Files:**
- Modify: `src/main/java/cn/tnar/parking/netty/handler/DispatcherHandler.java:60-67`

- [ ] **Step 1: 阅读当前代码**

```java
// DispatcherHandler.java 当前代码
log.debug("[SCHEDULER] 切换到 databaseScheduler, parkCode={}, type={}", parkCode, msg.getType());
responseMono
    .timeout(Duration.ofSeconds(TIMEOUT_SECONDS), ...)
    .doOnSubscribe(sub -> log.debug("[SCHEDULER] 开始 DB 任务，thread={}", Thread.currentThread().getName()))
    .subscribeOn(databaseScheduler)
    .publishOn(Schedulers.immediate())
    .doOnNext(r -> log.debug("[SCHEDULER] DB 任务完成，thread={}", Thread.currentThread().getName()))
```

- [ ] **Step 2: 更新日志描述**

```java
log.debug("[SCHEDULER] 开始在 databaseScheduler 执行 doHandle, parkCode={}, type={}", parkCode, msg.getType());
responseMono
    .timeout(Duration.ofSeconds(TIMEOUT_SECONDS), ...)
    .doOnSubscribe(sub -> log.debug("[SCHEDULER] doHandle 开始执行，thread={}", Thread.currentThread().getName()))
    .subscribeOn(databaseScheduler)
    .publishOn(Schedulers.immediate())
    .doOnNext(r -> log.debug("[SCHEDULER] doHandle 执行完成，thread={}", Thread.currentThread().getName()))
```

- [ ] **Step 3: 编译验证**

```bash
./gradlew compileJava --quiet
```

Expected: 编译通过

- [ ] **Step 4: 运行 Handler 测试**

```bash
./gradlew test --tests "*DispatcherHandler*" --quiet
```

Expected: 测试通过

- [ ] **Step 5: Commit**

```bash
git add src/main/java/cn/tnar/parking/netty/handler/DispatcherHandler.java
git commit -m "docs: 更新 DispatcherHandler 调度器日志描述"
```

---

### Task 5: 运行完整测试套件验证

**Files:**
- 无代码变更

- [ ] **Step 1: 运行完整测试**

```bash
./scripts/test-all.sh
```

Expected: 所有测试通过，无回归

- [ ] **Step 2: 验证日志输出（可选手动测试）**

```bash
./gradlew bootRun
# 运行测试客户端，观察日志中 databaseScheduler 线程名
```

Expected: 日志显示 `thread=db-scheduler-1` 等

---

## 自检验证

- [ ] **Spec 覆盖检查**: 所有三层调度器都已移除，统一由 `databaseScheduler` 管理
- [ ] **无占位符**: 每个步骤都有具体代码和命令
- [ ] **类型一致性**: 所有文件路径、方法名、类名与实际代码一致
- [ ] **测试覆盖**: 每个 Task 都有对应的测试验证步骤
