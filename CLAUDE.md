# Parking Netty Framework - 开发规范

> 本规范适用于 parking-netty-framework 项目，AI 助手和开发者需共同遵守

---

## 1. 项目概述

### 1.1 项目简介
基于 Netty 的高性能停车场通信中间件，支持万级并发连接和响应式数据处理。

### 1.2 技术栈
| 组件 | 版本 | 说明 |
|------|------|------|
| JDK | 21 (Alibaba Dragonwell) | 运行环境 |
| Spring Boot | 3.2.4 | 应用框架 |
| Netty | 4.1.108.Final | TCP 服务器 |
| Protobuf | 3.25.3 | 序列化 |
| Project Reactor | - | 响应式编程 |
| Redis | 6.0+ | 缓存（可选） |
| MySQL | 8.0+ | 数据库（可选） |
| H2 | 2.x | 嵌入式数据库（默认） |

### 1.3 项目结构

详见 `docs/DEVELOPMENT.md#22-项目结构`

---

## 2. 代码规范

### 2.1 命名规范
| 类型 | 规范 | 示例 |
|------|------|------|
| 类名 | 大驼峰 | `CarInHandler`, `AuthRepository` |
| 方法名 | 小驼峰 | `handle()`, `findBySerial()` |
| 变量名 | 小驼峰 | `parkCode`, `transmission` |
| 常量名 | 全大写 + 下划线 | `CODE_SUCCESS`, `MAX_RETRY` |
| 包名 | 全小写 | `cn.tnar.parking.netty.handler` |

### 2.2 代码风格
- **行长度**: ≤ 120 字符
- **方法长度**: ≤ 100 行
- **参数数量**: ≤ 8 个
- **圈复杂度**: ≤ 10
- **文件长度**: ≤ 500 行
- **缩进**: 4 空格（禁止 Tab）

### 2.3 检查命令
```bash
# Checkstyle 检查
./gradlew checkstyleMain checkstyleTest

# 查看报告
open build/reports/checkstyle/main.html
```

---

## 3. 架构规范

详见 `docs/DEVELOPMENT.md#3-核心组件`

### 3.1 Handler 开发规范

**必须继承 `AbstractHandler`**，统一的超时、日志、异常处理：

```java
@Slf4j
@Component
public class SpaceHandler extends AbstractHandler {
    public SpaceHandler(MetricsAspect metricsAspect) { super(metricsAspect); }
    
    @Override
    public Transmission.MessageType processedType() { return Transmission.MessageType.TYPE_SPACE; }
    
    @Override
    protected Respond doHandle(Transmission transmission, String parkCode) throws Exception {
        Space space = Space.parseFrom(transmission.getMessage());
        log.info("接收车位上报，parkCode: {}, 总车位：{}, 剩余车位：{}", parkCode, space.getTotal(), space.getRemained());
        return RespondProto.success();
    }
}
```

**关键点**:
1. 添加 `@Component` 由 Spring 管理
2. 通过构造函数注入 `MetricsAspect`
3. 实现 `processedType()` 返回消息类型
4. 业务逻辑写在 `doHandle()` 中

详见 `docs/DEVELOPMENT.md#32-handler-开发规范`

### 3.2 Repository 开发规范

**必须实现 `ReactiveAuthRepository` 接口**，返回 `Mono<T>`：

```java
@Component
@ConditionalOnProperty(prefix = "auth.storage", name = "type", havingValue = "redis")
public class RedisAuthRepository implements ReactiveAuthRepository {
    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private final String keyPrefix;
    
    @Override
    public Mono<AuthInfo> findBySerial(String serial) {
        return redisTemplate.opsForHash().multiGet(keyPrefix + serial, Arrays.asList("parkCode", "secret"))
            .flatMap(values -> {
                if (values == null || values.isEmpty()) return Mono.empty();
                return Mono.just(new AuthInfo(serial, values.get(0), values.get(1)));
            });
    }
}
```

**关键点**:
1. 使用 `@ConditionalOnProperty` 按需加载
2. 返回 `Mono<T>` 而非直接值，使用 `Mono.empty()` 表示空值
3. 阻塞操作使用 `Schedulers.boundedElastic()` 隔离

详见 `docs/DEVELOPMENT.md#33-repository-开发规范`

### 3.3 响应式编程规范

| 场景 | 正确做法 | 错误做法 |
|------|---------|---------|
| 空值处理 | `Mono.empty()` | `return null` |
| 阻塞操作隔离 | `subscribeOn(Schedulers.boundedElastic())` | 直接调用 JDBC |
| 链式处理 | `flatMap()`, `map()` | 嵌套回调 |

**测试示例**:
```java
@Test
void findBySerial_whenNotExists_returnsEmpty() {
    StepVerifier.create(repository.findBySerial("NOT-EXISTS")).verifyComplete();
}
```

详见 `docs/DEVELOPMENT.md#54-响应式编程指南`

### 3.4 调度器使用规范

| 调度器 | 用途 |
|--------|------|
| `databaseScheduler` | 数据库操作 |
| `externalScheduler` | 外部 API 调用 |
| `cpuScheduler` | CPU 密集型计算 |

详见 `docs/DEVELOPMENT.md#35-调度器使用规范`

---

## 4. 开发流程

### 4.1 添加新 Handler

1. 在 `message.proto` 中定义消息类型（如需要）
2. 运行 `./gradlew generateProto`
3. 创建 Handler 类继承 `AbstractHandler`，添加 `@Component`
4. 编写单元测试

### 4.2 测试规范

| 层级 | 命令 | 用途 |
|------|------|------|
| 快速测试 | `./gradlew test` | 开发中随时运行 |
| 完整测试 | `./scripts/test-all.sh` | 提交前运行 |
| Docker 测试 | `./scripts/docker-test.sh` | 部署前运行 |

**要求**: 所有 Handler 必须编写单元测试，覆盖率 ≥ 80%（核心模块 ≥ 90%）

详见 `docs/TESTING.md`

### 4.3 Git 提交与推送规范

**Commit Message**: 遵循 Conventional Commits，type 包括 `feat`/`fix`/`docs`/`style`/`refactor`/`test`/`chore`

**示例**:
```
feat: add payment handler for TYPE_PAYMENT

- Create PaymentHandler extending AbstractHandler
- Add unit tests with 100% coverage
```

**提交前检查**:
```bash
git status && git diff
./scripts/test-all.sh
./gradlew checkstyleMain checkstyleTest
```

**推送**:
```bash
git add <files>
git commit -m "feat: xxx"
git push -u origin <branch>
gh pr create --title "..." --body "..."
```

详见 `docs/DEVELOPMENT.md#43-git-提交与推送规范`

### 4.4 数据库与 Redis 配置

**数据库 Profile 切换**:
```yaml
spring:
  profiles:
    active: h2  # 可选：h2 | mysql
```

**Redis 条件化加载**:
```yaml
auth:
  storage:
    type: redis  # in-memory | redis | jdbc
```

详见 `docs/DEVELOPMENT.md#21-数据库配置` 和 `docs/PRODUCTION_DEPLOYMENT.md`

---

### 4.5 文档规范

**修改代码同步修改文档**：
1. 修改业务代码 → 更新 JavaDoc 注释
2. 修改公共 API → 更新 `CLAUDE.md` 对应章节
3. 新增功能模块 → 更新 `docs/DEVELOPMENT.md`
4. 修改配置项 → 更新 `docs/DEVELOPMENT.md` 和 `docs/PRODUCTION_DEPLOYMENT.md`

**提交前检查**:`git diff` 确认文档已同步更新

详见 `docs/DEVELOPMENT.md`

## 5. 常用命令

**构建**: `./gradlew generateProto` | `./gradlew build` | `./gradlew bootJar`

**测试**: `./gradlew test` | `./scripts/test-all.sh` | `./gradlew jacocoTestReport`

**代码质量**: `./gradlew checkstyleMain checkstyleTest`

**Docker**: `./scripts/build-docker.sh` | `./scripts/start.sh` | `docker compose logs -f`

详见 `docs/DEVELOPMENT.md#9-常用命令`

---

## 6. 配置说明与故障排查

**核心配置项**:

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `netty.tcp.port` | 8889 | TCP 端口 |
| `netty.tcp.auth-timeout` | 10 | 认证超时 (秒) |
| `auth.storage.type` | in-memory | 存储类型 |

**常见问题**:
- `Address already in use` → `netstat -tlnp | grep 8889` 查杀占用进程
- `Connection refused` → 检查服务状态和防火墙
- `Cannot get Jedis connection` → 检查 Redis 服务和密码配置
- Handler 未处理 → 确认添加 `@Component` 注解

详见 `docs/DEVELOPMENT.md#8-故障排查` 和 `docs/PRODUCTION_DEPLOYMENT.md`

---

## 7. 最佳实践

1. **继承 AbstractHandler** - 统一超时、日志、异常处理
2. **响应式 Repository** - 返回 `Mono<T>`，阻塞操作使用 `Schedulers.boundedElastic()`
3. **指标收集** - 通过 `MetricsAspect` 自动收集
4. **测试驱动** - 每次修改后运行快速测试
5. **文档同步** - 修改代码同步更新文档

详见 `docs/DEVELOPMENT.md#11-最佳实践`

---

## 8. 相关文档

- [开发指南](docs/DEVELOPMENT.md)
- [测试体系](docs/TESTING.md)
- [Docker 部署](docs/DOCKER_DEPLOYMENT.md)
- [生产部署](docs/PRODUCTION_DEPLOYMENT.md)
- [监控指南](docs/MONITORING.md)
- [响应式架构](docs/RESPONSIVE_ARCHITECTURE.md)
