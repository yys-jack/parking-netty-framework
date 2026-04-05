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
```
src/main/java/cn/tnar/parking/
├── NettyApplication.java          # 应用入口
├── config/                        # 配置类
├── framework/                     # 框架核心（AbstractHandler）
├── netty/                         # Netty 核心（ApiServer, Handler）
├── repository/                    # 数据访问层（响应式）
├── web/                           # REST 控制器
├── task/                          # 任务调度
├── aspect/                        # AOP 切面（指标收集）
└── model/                         # 模型类（异常、响应）
```

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

### 3.1 分层架构
```
┌─────────────────────────────────────┐
│         Web 层 (Controller)          │
│   - REST API, 缓存刷新              │
├─────────────────────────────────────┤
│         Netty 层 (Handler)           │
│   - TCP 消息处理，响应式             │
├─────────────────────────────────────┤
│       Repository 层 (响应式)         │
│   - InMemory / Redis / JDBC         │
├─────────────────────────────────────┤
│         基础设施层                   │
│   - 配置、指标、调度器               │
└─────────────────────────────────────┘
```

### 3.2 Handler 开发规范

**必须继承 `AbstractHandler`**，统一的超时、日志、异常处理：

```java
@Slf4j
@Component
public class SpaceHandler extends AbstractHandler {

    public SpaceHandler(MetricsAspect metricsAspect) {
        super(metricsAspect);
    }

    @Override
    public Transmission.MessageType processedType() {
        return Transmission.MessageType.TYPE_SPACE;
    }

    @Override
    protected Respond doHandle(Transmission transmission, String parkCode) throws Exception {
        Space space = Space.parseFrom(transmission.getMessage());
        log.info("SpaceHandler 接收车位上报，parkCode: {}, 总车位：{}, 剩余车位：{}",
                parkCode, space.getTotal(), space.getRemained());
        return RespondProto.success();
    }
}
```

**关键点**:
1. 添加 `@Component` 由 Spring 管理
2. 使用 `@Slf4j` 记录日志
3. 通过构造函数注入 `MetricsAspect`
4. 实现 `processedType()` 返回消息类型
5. 业务逻辑写在 `doHandle()` 中

### 3.3 Repository 开发规范

**必须实现 `ReactiveAuthRepository` 接口**，返回 `Mono<T>`：

```java
@Component
@ConditionalOnProperty(prefix = "auth.storage", name = "type", havingValue = "redis")
public class RedisAuthRepository implements ReactiveAuthRepository {

    private final ReactiveRedisTemplate<String, String> redisTemplate;

    @Override
    public Mono<AuthInfo> findBySerial(String serial) {
        String key = keyPrefix + serial;
        return redisTemplate
            .opsForHash()
            .multiGet(key, Arrays.asList("parkCode", "secret"))
            .flatMap(values -> {
                if (values == null || values.isEmpty()) {
                    return Mono.empty(); // 返回空 Mono，而非 null
                }
                return Mono.just(new AuthInfo(serial, values.get(0), values.get(1)));
            });
    }

    @Override
    public Mono<Boolean> validate(String serial, String secret) {
        return findBySerial(serial)
            .map(info -> info.getSecret().equals(secret))
            .defaultIfEmpty(false);
    }
}
```

**关键点**:
1. 使用 `@ConditionalOnProperty` 按需加载
2. 返回 `Mono<T>` 而非直接值
3. 使用 `Mono.empty()` 表示空值
4. 阻塞操作使用 `Schedulers.boundedElastic()` 隔离

### 3.4 响应式编程规范

**必须遵守的原则**:

| 场景 | 正确做法 | 错误做法 |
|------|---------|---------|
| 空值处理 | `Mono.empty()` | `return null` |
| 阻塞操作隔离 | `subscribeOn(Schedulers.boundedElastic())` | 直接调用 JDBC |
| 链式处理 | `flatMap()`, `map()` | 嵌套回调 |
| 默认值 | `defaultIfEmpty(value)` | 手动判断 null |

**测试响应式代码** (使用 `StepVerifier`):

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

### 3.5 调度器使用规范

项目提供三种专用调度器：

```java
@Autowired
@Qualifier("databaseScheduler")
private Scheduler databaseScheduler;

@Autowired
@Qualifier("externalScheduler")
private Scheduler externalScheduler;

// CPU 密集型任务
@Autowired
private Scheduler cpuScheduler;
```

| 调度器 | 用途 | 线程池配置 |
|--------|------|-----------|
| `databaseScheduler` | 数据库操作 | 核心 10, 最大 20 |
| `externalScheduler` | 外部 API 调用 | 核心 5, 最大 10 |
| `cpuScheduler` | CPU 密集型计算 | CPU 核心数相关 |

---

## 4. 开发流程

### 4.1 添加新 Handler

1. 在 `message.proto` 中定义消息类型（如需要）
2. 运行 `./gradlew generateProto` 生成 Java 类
3. 创建 Handler 类继承 `AbstractHandler`
4. 添加 `@Component` 注解
5. 编写单元测试

### 4.2 测试规范

**测试分层**:
| 层级 | 命令 | 时间 | 用途 |
|------|------|------|------|
| 快速测试 | `./gradlew test` | ~60s | 开发中随时运行 |
| 完整测试 | `./scripts/test-all.sh` | ~90s | 提交前运行 |
| Docker 测试 | `./scripts/docker-test.sh` | ~120s | 部署前运行 |

**测试覆盖范围**:
- 所有 Handler 必须编写单元测试
- Repository 必须测试三种场景（成功/空/失败）
- 新业务逻辑必须添加集成测试

### 4.3 Git 提交与推送规范

#### Commit Message 格式

遵循 Conventional Commits 规范：

```
<type>: <subject>

[optional body]
```

**type 类型**:

| type | 说明 | 示例 |
|------|------|------|
| `feat` | 新功能 | `feat: add payment handler` |
| `fix` | Bug 修复 | `fix: resolve NPE in CarInHandler` |
| `docs` | 文档更新 | `docs: add deployment guide` |
| `style` | 代码风格（不影响功能） | `style: format handler classes` |
| `refactor` | 重构 | `refactor: extract auth logic` |
| `test` | 测试相关 | `test: add RedisConnectionConfig tests` |
| `chore` | 构建/工具配置 | `chore: update gradle wrapper` |

**subject 规则**:
- 使用祈使句（"add" 而非 "added" 或 "adds"）
- 首字母不大写
- 末尾无句号
- 长度 ≤ 50 字符

**完整示例**:
```
feat: add payment handler for TYPE_PAYMENT

- Create PaymentHandler extending AbstractHandler
- Add payment message processing logic
- Add unit tests with 100% coverage

Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>
```

#### 分支命名规范

| 分支类型 | 命名格式 | 示例 |
|---------|---------|------|
| 功能分支 | `feature/<description>` | `feature/payment-handler` |
| 修复分支 | `fix/<description>` | `fix/auth-timeout-bug` |
| 文档分支 | `docs/<description>` | `docs/deployment-guide` |
| 重构分支 | `refactor/<description>` | `refactor/auth-repository` |

#### 提交流程

**1. 提交前检查清单**:

```bash
# 1. 查看变更
git status
git diff

# 2. 运行完整测试套件
./scripts/test-all.sh

# 3. 检查代码风格
./gradlew checkstyleMain checkstyleTest

# 4. 查看测试覆盖率
open build/reports/jacoco/test/html/index.html

# 5. 查看 Checkstyle 报告
open build/reports/checkstyle/main.html
```

**2. 提交代码**:

```bash
# 添加变更（避免使用 -A，只添加相关文件）
git add src/main/java/cn/tnar/parking/netty/handler/PaymentHandler.java
git add src/test/java/cn/tnar/parking/netty/handler/PaymentHandlerTest.java

# 创建提交
git commit -m "feat: add payment handler for TYPE_PAYMENT"
```

**注意**: 不要使用 `git add -A` 或 `git add .`，可能意外提交敏感文件（`.env`, 配置文件等）。

#### GitHub CLI 推送工作流

**1. 认证 GitHub CLI**:

```bash
# 首次使用需要认证
gh auth login
# 选择 GitHub.com → SSH → 按提示完成认证
```

**2. 推送功能分支**:

```bash
# 推送到远程仓库
git push -u origin feature/payment-handler
```

**3. 创建 Pull Request**:

```bash
# 创建 PR
gh pr create \
  --title "feat: add payment handler for TYPE_PAYMENT" \
  --body "$(cat <<'EOF'
## Summary
- Add PaymentHandler for TYPE_PAYMENT messages
- Implement payment processing logic
- Add unit tests with 100% coverage

## Test plan
- [ ] ./gradlew test passed
- [ ] ./scripts/test-all.sh passed
- [ ] Checkstyle passed
- [ ] Coverage report reviewed

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

**4. 查看 PR 状态**:

```bash
# 查看 PR 列表
gh pr list

# 查看当前 PR 详情
gh pr view

# 查看 CI 运行状态
gh run list

# 查看详细日志
gh run view --log
```

**5. 合并 PR**（维护者）:

```bash
# 查看可合并的 PR
gh pr list --state success

# 合并 PR（squash merge）
gh pr merge --squash --auto
```

#### 代码审查要求

**提交前自查**:
- [ ] 所有新增代码都有对应的测试
- [ ] 测试覆盖率 ≥ 80%（核心模块 ≥ 90%）
- [ ] Checkstyle 无新增警告
- [ ] Commit Message 符合 Conventional Commits 规范
- [ ] 分支命名符合规范

**PR 审查重点**:
- [ ] 功能是否按预期实现
- [ ] 是否有遗漏的边界情况
- [ ] 代码是否符合项目规范
- [ ] 是否有性能或安全隐患
- [ ] 是否有不必要的复杂度

#### 注意事项

1. **敏感文件**: 永远不要提交 `.env`, `auth.yml` 等包含敏感信息的文件
2. **大文件**: 单个文件变更不超过 500 行（重构除外）
3. **提交粒度**: 每个提交只做一件事，便于回滚和审查
4. **Force Push**: 禁止在共享分支使用 `git push --force`
5. **CI 检查**: PR 必须通过所有 CI 检查才能合并

### 4.4 数据库配置

**Profile 切换**:
```yaml
# application.yml
spring:
  profiles:
    active: h2  # 可选：h2 | mysql
```

**命令行切换**:
```bash
# 使用 MySQL
./gradlew bootRun --args='--spring.profiles.active=mysql'
```

### 4.5 Redis 条件化配置

Redis 配置根据 `auth.storage.type` 按需加载：
- `type=redis` → 加载 `RedisAutoConfiguration`
- `type=in-memory` 或 `jdbc` → 不加载 Redis 配置

```yaml
auth:
  storage:
    type: redis  # in-memory | redis | jdbc
```

---

## 5. 常用命令

### 5.1 构建和运行
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

### 5.2 测试
```bash
# 快速测试
./gradlew test

# 运行特定测试
./gradlew test --tests "SpaceHandlerTest"

# 完整测试套件
./scripts/test-all.sh

# 查看测试报告
open build/reports/tests/test/index.html
```

### 5.3 代码质量
```bash
# Checkstyle 检查
./gradlew checkstyleMain checkstyleTest

# 覆盖率报告
./gradlew jacocoTestReport

# 查看报告
open build/reports/checkstyle/main.html
open build/reports/jacoco/test/html/index.html
```

### 5.4 Docker
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

## 6. 配置说明

### 6.1 核心配置项

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `server.port` | 8080 | HTTP 端口 (Actuator) |
| `netty.tcp.port` | 8889 | TCP 端口 |
| `netty.tcp.auth-timeout` | 10 | 认证超时时间 (秒) |
| `netty.tcp.heartbeat-interval` | 90 | 心跳间隔 (秒) |
| `netty.tcp.backlog` | 128 | 连接队列大小 |
| `auth.storage.type` | in-memory | 存储类型 |
| `spring.profiles.active` | h2 | 数据库 Profile |

### 6.2 监控指标

| 指标名称 | 类型 | 说明 |
|---------|------|------|
| `parking_netty_connections_active` | Gauge | 活跃连接数 |
| `parking_netty_auth_success_total` | Counter | 认证成功次数 |
| `parking_netty_auth_failure_total` | Counter | 认证失败次数 |
| `parking_netty_messages_total` | Counter | 消息处理总数 |
| `parking_netty_timeout_total` | Counter | 超时次数 |

**访问指标**: `http://localhost:8080/actuator/prometheus`

---

## 7. 故障排查

### 7.1 常见问题

| 问题 | 可能原因 | 解决方案 |
|------|---------|---------|
| `Address already in use` | 端口被占用 | `netstat -tlnp \| grep 8889` 查杀 |
| `Connection refused` | 服务未启动 | 检查防火墙和服务状态 |
| `Cannot get Jedis connection` | Redis 连接失败 | 检查 Redis 服务和密码配置 |
| Handler 未处理 | 未添加 `@Component` | 添加注解并重启 |

### 7.2 日志级别

开发环境使用 DEBUG，生产环境使用 INFO：
```yaml
logging:
  level:
    cn.tnar.parking: DEBUG  # 生产改为 INFO
    io.netty: INFO
```

---

## 8. 最佳实践

1. **继承 AbstractHandler** - 统一超时、日志、异常处理
2. **使用响应式 Repository** - 返回 `Mono<T>`，避免阻塞
3. **阻塞操作隔离** - JDBC 等操作使用 `Schedulers.boundedElastic()`
4. **异常处理** - 使用 `BusinessException` 处理业务异常
5. **指标收集** - 通过 `MetricsAspect` 自动收集指标
6. **频繁测试** - 每次修改后运行快速测试
7. **提交前验证** - 提交前运行完整测试套件
8. **代码风格** - 遵守 Checkstyle 规范

---

## 9. 相关文档

- [开发指南](docs/DEVELOPMENT.md)
- [测试体系](docs/TESTING.md)
- [Docker 部署](docs/DOCKER_DEPLOYMENT.md)
- [生产部署](docs/PRODUCTION_DEPLOYMENT.md)
- [监控指南](docs/MONITORING.md)
- [响应式架构](docs/RESPONSIVE_ARCHITECTURE.md)
