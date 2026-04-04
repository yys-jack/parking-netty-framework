# 测试体系使用指南

**日期**: 2026-04-04  
**状态**: 已启用

---

## 1. 概述

本项目采用分层测试策略，确保代码质量和快速反馈：

```
执行频率
    ▲
    │                    ╱╲
    │                   ╱  ╲
    │                  ╱    ╲
    │                 ╱ Docker ╲
    │                ╱  测试     ╲
    │               ╱────────────╲
    │              ╱  完整测试     ╲
    │             ╱  (集成 + 覆盖率) ╲
    │            ╱──────────────────╲
    │           ╱    快速测试         ╲
    │          ╱    (单元测试)         ╲
    │         ╱────────────────────────╲
    │
    └────────────────────────────────────▶ 执行时间
       5-10s        30-60s        1-2min
```

---

## 2. 测试层级

### 2.1 快速测试 (Quick Tests)

**命令**: `./gradlew test`

**执行时间**: 5-10 秒

**用途**: 
- 开发过程中随时运行
- 验证代码修改未破坏现有功能
- 快速反馈测试结果

**测试范围**:
- 所有单元测试
- 所有 Handler 测试
- 集成测试

**输出示例**:
```
> Task :test

CarInHandlerTest > testCarIn() PASSED
CarOutHandlerTest > testCarOut() PASSED
DemoHandlerTest > testDemo() PASSED
SpaceHandlerTest > testSpaceReport() PASSED
RespondProtoTest > testSuccess() PASSED
HandlerRegistryTest > testRegistry() PASSED
ChannelMaintainerTest > testGetChannel() PASSED
OperationResultFutureTest > testComplete() PASSED
AuthIntegrationTest > testAuthentication() PASSED

BUILD SUCCESSFUL in 1.435s
```

**报告位置**: `build/reports/tests/test/index.html`

---

### 2.2 完整测试 (Full Test Suite)

**命令**: `./scripts/test-all.sh`

**执行时间**: 30-60 秒

**用途**:
- Git commit 前运行
- 验证代码质量和覆盖率
- 确保符合代码风格规范

**执行步骤**:
1. `./gradlew clean test` - 清理并运行所有测试
2. `./gradlew jacocoTestReport` - 生成代码覆盖率报告
3. `./gradlew checkstyleMain checkstyleTest` - 代码风格检查
4. `./gradlew build -x test` - 构建验证

**输出示例**:
```
========================================
Running Full Test Suite
========================================
[1/4] Running unit tests...
BUILD SUCCESSFUL in 8s

[2/4] Generating code coverage report...
BUILD SUCCESSFUL in 3s

[3/4] Running checkstyle...
BUILD SUCCESSFUL in 5s

[4/4] Verifying build...
BUILD SUCCESSFUL in 10s

========================================
All tests passed!
========================================

Test report: build/reports/tests/test/index.html
Coverage report: build/reports/jacoco/test/html/index.html
```

**报告位置**:
- 测试报告：`build/reports/tests/test/index.html`
- 覆盖率报告：`build/reports/jacoco/test/html/index.html`
- Checkstyle 报告：`build/reports/checkstyle/main.html`

---

### 2.3 Docker 测试

**命令**: `./scripts/docker-test.sh`

**执行时间**: 1-2 分钟

**用途**:
- 部署前验证
- 验证 Docker 镜像构建
- 验证容器健康状态

**测试内容**:
1. 构建测试镜像
2. 启动测试容器
3. 健康检查：
   - Java 版本验证
   - HTTP 端点检查 (`/actuator/health`)
   - TCP 端口检查 (8889)
4. 清理容器

**输出示例**:
```
========================================
Running Docker Test
========================================
[1/4] Building test image...
Successfully built abc123

[2/4] Starting test container...
Container started

[3/4] Running health checks...
  - Checking Java version...
  openjdk version "21.0.10"
  - Checking HTTP health...
  {"status":"UP"}
  - Checking TCP port...
  Connection succeeded

[4/4] Cleaning up...
Container stopped and removed

========================================
Docker test passed!
========================================
```

---

## 3. Git Hooks

### 3.1 安装

```bash
./scripts/install-hooks.sh
```

### 3.2 Pre-commit Hook

**触发时机**: Git commit 时自动执行

**执行内容**: `./gradlew test --quiet`

**输出示例**:
```bash
$ git commit -m "feat: add new feature"
Running pre-commit tests...
✅ Pre-commit tests passed!
[main 1234567] feat: add new feature
```

**失败处理**:
```bash
$ git commit -m "feat: broken code"
Running pre-commit tests...
❌ Tests failed! Commit aborted.
```

### 3.3 绕过 Hook (不推荐)

```bash
git commit --no-verify -m "emergency fix"
```

---

## 4. 测试覆盖

### 4.1 单元测试

| 测试类 | 测试数 | 说明 |
|--------|--------|------|
| `RespondProtoTest` | 5 | 响应工厂测试 |
| `HandlerRegistryTest` | 2 | Handler 注册表测试 |
| `ChannelMaintainerTest` | 3 | Channel 管理器测试 |
| `OperationResultFutureTest` | 4 | 异步 Future 测试 |

### 4.2 Handler 测试

| 测试类 | 测试数 | 说明 |
|--------|--------|------|
| `SpaceHandlerTest` | 2 | 车位上报 Handler |
| `CarInHandlerTest` | 3 | 车辆入场 Handler |
| `CarOutHandlerTest` | 2 | 车辆出场 Handler |
| `DemoHandlerTest` | 2 | 演示 Handler |

### 4.3 集成测试

| 测试类 | 测试数 | 说明 |
|--------|--------|------|
| `AuthIntegrationTest` | 5 | 认证集成测试 |

**总计**: 28 个测试，100% 通过率

---

## 5. 代码质量

### 5.1 JaCoCo 代码覆盖率

**配置**:
```gradle
jacoco {
    toolVersion = "0.8.11"
}

jacocoTestReport {
    dependsOn test
    reports {
        xml.required = true
        html.required = true
    }
}
```

**查看报告**:
```bash
./gradlew jacocoTestReport
open build/reports/jacoco/test/html/index.html
```

### 5.2 Checkstyle 代码风格

**配置**:
```gradle
checkstyle {
    toolVersion = "10.12.5"
    configFile = file("${rootDir}/checkstyle.xml")
}
```

**检查规则**:
- 命名规范（类名、方法名、变量名）
- 代码块规范（避免空块、嵌套块）
- 导入规范（避免星号导入、冗余导入）
- 复杂度限制（方法长度 ≤100 行，参数 ≤8 个，圈复杂度 ≤10）
- 行长度限制（≤120 字符）

**运行检查**:
```bash
./gradlew checkstyleMain checkstyleTest
open build/reports/checkstyle/main.html
```

---

## 6. 开发工作流

### 6.1 日常开发

```bash
# 1. 修改代码

# 2. 运行快速测试
./gradlew test

# 3. 查看测试报告（可选）
open build/reports/tests/test/index.html
```

### 6.2 提交前

```bash
# 1. 运行完整测试套件
./scripts/test-all.sh

# 2. 或安装 git hooks 后自动运行
git commit -m "feat: xxx"
```

### 6.3 部署前

```bash
# 1. 运行 Docker 测试
./scripts/docker-test.sh

# 2. 构建并部署
docker compose build
docker compose up -d
```

---

## 7. 环境要求

| 环境 | 版本 | 说明 |
|------|------|------|
| JDK | 21 (Alibaba Dragonwell 21.0.10) | 测试运行环境 |
| Gradle | 8.5 | 构建工具 |
| Docker | 20+ | Docker 测试需要 |

**JAVA_HOME 配置**:
```bash
export JAVA_HOME=/home/yy/java/dragonwell-21.0.10
export PATH=$JAVA_HOME/bin:$PATH
```

---

## 8. 故障排查

### 测试失败

```bash
# 运行单个测试类
./gradlew test --tests "CarInHandlerTest"

# 查看详细信息
./gradlew test --info
```

### Checkstyle 失败

```bash
# 查看具体错误
./gradlew checkstyleMain --info

# 查看报告
open build/reports/checkstyle/main.html
```

### Docker 测试失败

```bash
# 手动检查镜像
docker images parking-netty-framework

# 手动启动容器
docker run -d --name test -p 8080:8080 -p 8889:8889 parking-netty-framework:test

# 查看容器日志
docker logs test

# 清理
docker stop test && docker rm test
```

---

## 9. 文件清单

| 文件 | 用途 |
|------|------|
| `build.gradle` | JaCoCo 和 Checkstyle 配置 |
| `checkstyle.xml` | Checkstyle 规则配置 |
| `scripts/test-all.sh` | 完整测试套件脚本 |
| `scripts/docker-test.sh` | Docker 测试脚本 |
| `scripts/install-hooks.sh` | Git Hooks 安装脚本 |
| `scripts/hooks/pre-commit` | Pre-commit hook 模板 |

---

## 10. 最佳实践

1. **频繁运行快速测试** - 每次修改后运行，快速发现问题
2. **提交前运行完整测试** - 确保代码质量和风格
3. **部署前运行 Docker 测试** - 验证容器化部署
4. **保持 Git Hooks 启用** - 防止问题代码入库
5. **查看测试报告** - 了解测试覆盖情况
6. **修复 Checkstyle 警告** - 保持代码风格一致
