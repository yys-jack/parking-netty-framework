# Parking Netty Framework - 快速参考

> 5 分钟上手 · 按场景查阅

---

## 1. 快速开始

**环境**: JDK 21 | **启动**: `./gradlew bootRun` | **测试**: `./gradlew test`

**添加 Handler**: 继承 `AbstractHandler` → 加 `@Component` → 实现 `processedType()`/`doHandle()` → 写测试

---

## 2. 代码规范

**命名**: 类名大驼峰 (`CarInHandler`) | 方法小驼峰 (`findBySerial()`) | 常量全大写 (`CODE_SUCCESS`)

**红线**: 行≤120 | 方法≤100 行 | 参数≤8 个 | 圈复杂度≤10 | 缩进 4 空格

**示例**:
```java
@Slf4j
@Component
public class SpaceHandler extends AbstractHandler {
    public SpaceHandler(MetricsAspect metricsAspect) { super(metricsAspect); }
    @Override
    public Transmission.MessageType processedType() { return Transmission.MessageType.TYPE_SPACE; }
    @Override
    protected Respond doHandle(Transmission transmission, String parkCode) {
        log.info("接收车位上报，parkCode: {}", parkCode);
        return RespondProto.success();
    }
}
```

**响应式**: 返回 `Mono<T>` | 空值 `Mono.empty()` | 阻塞操作加 `subscribeOn(Schedulers.boundedElastic())`

---

## 3. 开发清单

### 3.1 添加功能
- [ ] 修改 proto → `./gradlew generateProto`
- [ ] 实现 Handler/Repository
- [ ] 编写单元测试 (覆盖率 ≥ 80%)
- [ ] 更新文档

### 3.2 提交前检查 (**测试必须通过**)
```bash
git status && git diff
./scripts/test-all.sh          # 测试必须通过 ✅
./gradlew checkstyleMain test  # 代码风格 + 测试
git diff docs/ CLAUDE.md       # 确认文档已更新
```

### 3.3 Git Worktree

```bash
git worktree add ../parking-feature feature/xxx  # 创建工作树
cd ../parking-feature                             # 切换
git worktree remove feature/xxx                   # 完成后删除
```

**Commit Message**: `feat:` 新功能 | `fix:` 修复 | `docs:` 文档 | `refactor:` 重构 | `test:` 测试 | `chore:` 构建

### 3.4 文档规范
| 修改内容 | 更新文档 |
|---------|---------|
| 业务代码/公共 API | JavaDoc + CLAUDE.md |
| 功能模块/配置项 | DEVELOPMENT.md + PRODUCTION_DEPLOYMENT.md |

---

## 4. 文档索引

| 场景 | 文档 | 场景 | 文档 |
|------|------|------|------|
| 添加 Handler | DEVELOPMENT.md#4.1 | 数据库配置 | DEVELOPMENT.md#2.1 |
| 消息类型 | DEVELOPMENT.md#5.2 | Redis 配置 | PRODUCTION_DEPLOYMENT.md#3 |
| 响应式编程 | DEVELOPMENT.md#5.4 | 单元测试 | TESTING.md#6.1 |
| Docker 部署 | DOCKER_DEPLOYMENT.md | 监控 | MONITORING.md |
| 故障排查 | DEVELOPMENT.md#8 | 消息幂等性 | DEVELOPMENT.md#5.7 |
| 完整文档 | 见下方列表 |

---

## 5. 常用命令

**构建**: `./gradlew generateProto` · `build` · `bootJar`

**测试**: `./gradlew test` · `./scripts/test-all.sh` · `jacocoTestReport`

**质量**: `./gradlew checkstyleMain checkstyleTest`

**Docker**: `./scripts/build-docker.sh` · `start.sh` · `docker compose logs -f`

**Git**: `git add` · `commit -m "feat: xxx"` · `push` · `gh pr create`

---

## 6. 完整文档

[DEVELOPMENT.md](docs/DEVELOPMENT.md) · [TESTING.md](docs/TESTING.md) · [DOCKER_DEPLOYMENT.md](docs/DOCKER_DEPLOYMENT.md) · [PRODUCTION_DEPLOYMENT.md](docs/PRODUCTION_DEPLOYMENT.md) · [MONITORING.md](docs/MONITORING.md) · [RESPONSIVE_ARCHITECTURE.md](docs/RESPONSIVE_ARCHITECTURE.md)
