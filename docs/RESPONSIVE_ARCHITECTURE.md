# 响应式架构指南

**日期**: 2026-04-05
**状态**: 已启用

---

## 1. 架构概述

本项目使用 Project Reactor 实现响应式认证存储。

**核心接口**: `ReactiveAuthRepository`

```java
public interface ReactiveAuthRepository {
    Mono<AuthInfo> findBySerial(String serial);
    Mono<Boolean> validate(String serial, String secret);
}
```

**实现**:
- `InMemoryAuthRepository` - 内存实现（默认）
- `RedisAuthRepository` - Redis 实现（生产推荐）
- `JdbcAuthRepository` - JDBC 实现（备用）

---

## 2. Project Reactor 基础

### 2.1 Mono<T>

表示 0 或 1 个异步结果：

```java
// 返回一个值
Mono.just(value);

// 返回空
Mono.empty();

// 从 Callable 创建
Mono.fromCallable(() -> database.query());
```

### 2.2 常用操作符

```java
// 映射转换
mono.map(x -> x.toString());

// 链式处理
mono.flatMap(this::anotherMonoMethod);

// 提供默认值
mono.defaultIfEmpty(defaultValue);

// 异步订阅
mono.subscribe(
    result -> { /* 成功 */ },
    error -> { /* 失败 */ }
);
```

---

## 3. ReactiveAuthRepository 实现

### 3.1 Redis 实现

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
                    return Mono.empty();
                }
                return Mono.just(new AuthInfo(serial, values.get(0), values.get(1)));
            });
    }

    @Override
    public Mono<Boolean> validate(String serial, String secret) {
        return findBySerial(serial)
            .map(authInfo -> authInfo.getSecret().equals(secret))
            .defaultIfEmpty(false);
    }

    @PostConstruct
    public void loadFromDatabase() {
        // 服务启动时从数据库加载到 Redis
    }
}
```

### 3.2 JDBC 实现

```java
@Repository
@RequiredArgsConstructor
public class JdbcAuthRepository implements ReactiveAuthRepository {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public Mono<AuthInfo> findBySerial(String serial) {
        return Mono.fromCallable(() -> {
            return jdbcTemplate.queryForObject(
                "SELECT * FROM auth_info WHERE serial = ?",
                new BeanPropertyRowMapper<>(AuthInfo.class),
                serial
            );
        });
        // 无需 subscribeOn，由 DispatcherHandler 的 databaseScheduler 统一调度
    }

    @Override
    public Mono<Boolean> validate(String serial, String secret) {
        return findBySerial(serial)
            .map(authInfo -> authInfo.getSecret().equals(secret))
            .defaultIfEmpty(false);
    }
}
```

---

## 4. StepVerifier 测试

### 4.1 测试成功场景

```java
@Test
void findBySerial_whenExists_returnsAuthInfo() {
    // 准备数据
    redisTemplate.opsForHash().put("parking:auth:TEST-001", "parkCode", "PARK001");
    redisTemplate.opsForHash().put("parking:auth:TEST-001", "secret", "test-secret");

    Mono<AuthInfo> result = repository.findBySerial("TEST-001");

    StepVerifier.create(result)
        .assertNext(auth -> {
            assertEquals("PARK001", auth.getParkCode());
            assertEquals("test-secret", auth.getSecret());
        })
        .verifyComplete();
}
```

### 4.2 测试空结果

```java
@Test
void findBySerial_whenNotExists_returnsEmpty() {
    Mono<AuthInfo> result = repository.findBySerial("NOT-EXISTS");

    StepVerifier.create(result)
        .verifyComplete(); // 验证 Mono.empty()
}
```

---

## 5. 最佳实践

1. **始终使用 Mono.empty()** - 不要返回 `null`
2. **阻塞操作隔离** - 由 `databaseScheduler` 统一调度（在 DispatcherHandler 层）
3. **异常处理** - 使用 `onErrorResume()` 提供降级
4. **测试完整** - 使用 `StepVerifier` 覆盖所有场景

---

## 6. 统一调度器架构

**日期**: 2026-04-06  
**状态**: 已启用

### 6.1 架构演进

早期实现中，每个 Repository 层都使用 `Schedulers.boundedElastic()` 进行线程隔离：

```
DispatcherHandler (无调度器)
    ↓
AbstractHandler (subscribeOn boundedElastic)  ← 冗余
    ↓  
Repository (subscribeOn boundedElastic)       ← 冗余
```

实际上，只有最内层的 `subscribeOn` 生效，外层的调度器被覆盖，导致配置无法统一管理。

### 6.2 当前架构

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

### 6.3 配置示例

```yaml
scheduler:
  database:
    core-threads: 10
    max-threads: 50
    queue-capacity: 100
```

### 6.4 代码示例

**Repository 层** - 无需 `subscribeOn`：

```java
@Repository
public class JdbcAuthRepository implements ReactiveAuthRepository {
    
    private final JdbcTemplate jdbcTemplate;
    
    @Override
    public Mono<AuthInfo> findBySerial(String serial) {
        return Mono.fromCallable(() -> {
            // JDBC 查询逻辑
            return jdbcTemplate.queryForObject(...);
        });
        // 无需 subscribeOn，由 DispatcherHandler 的 databaseScheduler 统一调度
    }
}
```

**DispatcherHandler 层** - 统一调度点：

```java
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
