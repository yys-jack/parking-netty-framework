# Parking Netty Framework - 快速参考

> Worktree 友好型开发手册 · 快速查找 · 按需查阅

---

## 1. 快速开始

**环境**: JDK 21 (Alibaba Dragonwell)

```bash
# 启动应用
./gradlew bootRun

# 运行测试
./gradlew test

# 打包
./gradlew bootJar
```

**添加 Handler** (3 步):
1. 创建类继承 `AbstractHandler`，加 `@Component`
2. 实现 `processedType()` 和 `doHandle()`
3. 编写单元测试

**配置文件**: `application.yml` (默认 H2)，`auth.yml` (认证配置)

---

## 2. 代码规范

### 2.1 命名规范

| 类型 | 规范 | 示例 |
|------|------|------|
| 类名 | 大驼峰 | `CarInHandler` |
| 方法名 | 小驼峰 | `findBySerial()` |
| 变量名 | 小驼峰 | `parkCode` |
| 常量名 | 全大写 | `CODE_SUCCESS` |
| 包名 | 全小写 | `cn.tnar.parking.netty` |

### 2.2 代码风格 (红线)

- 行长度 ≤ 120 字符
- 方法长度 ≤ 100 行
- 参数 ≤ 8 个
- 圈复杂度 ≤ 10
- 缩进 4 空格 (禁止 Tab)

### 2.3 Handler 示例

```java
@Slf4j
@Component
public class SpaceHandler extends AbstractHandler {
    public SpaceHandler(MetricsAspect metricsAspect) { super(metricsAspect); }
    
    @Override
    public Transmission.MessageType processedType() { return Transmission.MessageType.TYPE_SPACE; }
    
    @Override
    protected Respond doHandle(Transmission transmission, String parkCode) {
        Space space = Space.parseFrom(transmission.getMessage());
        log.info("接收车位上报，parkCode: {}, 总车位：{}", parkCode, space.getTotal());
        return RespondProto.success();
    }
}
```

### 2.4 Repository 示例

```java
@Component
@ConditionalOnProperty(prefix = "auth.storage", name = "type", havingValue = "redis")
public class RedisAuthRepository implements ReactiveAuthRepository {
    private final ReactiveRedisTemplate<String, String> redisTemplate;
    
    @Override
    public Mono<AuthInfo> findBySerial(String serial) {
        return redisTemplate.opsForHash().multiGet(keyPrefix + serial, ...)
            .flatMap(values -> values.isEmpty() ? Mono.empty() : Mono.just(...));
    }
}
```

**响应式原则**:
- 返回 `Mono<T>`，空值用 `Mono.empty()`
- 阻塞操作加 `subscribeOn(Schedulers.boundedElastic())`

---

## 3. 开发清单

### 3.1 添加功能

- [ ] 修改 proto (如需要) → `./gradlew generateProto`
- [ ] 实现 Handler/Repository
- [ ] 编写单元测试 (覆盖率 ≥ 80%)
- [ ] 更新 JavaDoc 和文档

### 3.2 提交前检查

```bash
# 1. 查看变更
git status && git diff

# 2. 运行测试
./scripts/test-all.sh

# 3. 代码风格检查
./gradlew checkstyleMain checkstyleTest

# 4. 确认文档已更新
git diff docs/ CLAUDE.md
```

### 3.3 Git Worktree 模式

```bash
# 创建工作树 (隔离开发)
git worktree add ../parking-feature feature/xxx

# 切换到工作树
cd ../parking-feature

# 完成后删除
git worktree remove feature/xxx
```

**Commit Message**:
```
feat: add payment handler

- Create PaymentHandler
- Add unit tests

Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>
```

### 3.4 文档规范

| 修改内容 | 更新文档 |
|---------|---------|
| 业务代码 | JavaDoc |
| 公共 API | CLAUDE.md |
| 功能模块 | DEVELOPMENT.md |
| 配置项 | DEVELOPMENT.md + PRODUCTION_DEPLOYMENT.md |

---

## 4. 文档索引

### 4.1 开发场景

| 场景 | 文档 | 章节 |
|------|------|------|
| 添加 Handler | `docs/DEVELOPMENT.md` | 4.1 |
| 添加消息类型 | `docs/DEVELOPMENT.md` | 5.2 |
| 响应式编程 | `docs/DEVELOPMENT.md` | 5.4 |
| 数据库配置 | `docs/DEVELOPMENT.md` | 2.1 |
| Redis 配置 | `docs/PRODUCTION_DEPLOYMENT.md` | 3 |

### 4.2 测试场景

| 场景 | 文档 | 章节 |
|------|------|------|
| 单元测试 | `docs/TESTING.md` | 6.1 |
| 集成测试 | `docs/TESTING.md` | 6.2 |
| 覆盖率报告 | `docs/TESTING.md` | 4.2 |

### 4.3 部署场景

| 场景 | 文档 | 章节 |
|------|------|------|
| Docker 部署 | `docs/DOCKER_DEPLOYMENT.md` | - |
| 生产部署 | `docs/PRODUCTION_DEPLOYMENT.md` | - |
| 监控配置 | `docs/MONITORING.md` | - |

### 4.4 故障排查

| 问题 | 解决方案 |
|------|---------|
| 端口占用 | `netstat -tlnp \| grep 8889` |
| 连接拒绝 | 检查服务和防火墙 |
| Redis 连接失败 | 检查 Redis 服务和密码 |
| Handler 未处理 | 确认 `@Component` 注解 |

详见 `docs/DEVELOPMENT.md#8-故障排查`

---

## 5. 常用命令

**构建**: `./gradlew generateProto` · `./gradlew build` · `./gradlew bootJar`

**测试**: `./gradlew test` · `./scripts/test-all.sh` · `./gradlew jacocoTestReport`

**质量**: `./gradlew checkstyleMain checkstyleTest`

**Docker**: `./scripts/build-docker.sh` · `./scripts/start.sh` · `docker compose logs -f`

**GitHub CLI**: `gh pr create` · `gh pr list` · `gh run view --log`

---

## 6. 完整文档

- [开发指南](docs/DEVELOPMENT.md)
- [测试体系](docs/TESTING.md)
- [Docker 部署](docs/DOCKER_DEPLOYMENT.md)
- [生产部署](docs/PRODUCTION_DEPLOYMENT.md)
- [监控指南](docs/MONITORING.md)
- [响应式架构](docs/RESPONSIVE_ARCHITECTURE.md)
