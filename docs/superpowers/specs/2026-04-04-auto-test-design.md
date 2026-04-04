# 自动测试体系设计方案

**日期**: 2026-04-04  
**作者**: Claude Code  
**需求**: 建立完整的自动化测试体系，支持本地开发、提交前验证、部署前验证的分层测试策略

---

## 1. 背景

当前项目已有 12 个测试文件，覆盖单元测试、Handler 测试和集成测试。需要建立自动化测试体系，确保代码修改后的回归测试。

### 当前测试资源

| 类型 | 文件 | 说明 |
|------|------|------|
| 单元测试 | `RespondProtoTest` | 响应工厂测试 |
| 单元测试 | `HandlerRegistryTest` | Handler 注册表测试 |
| 单元测试 | `ChannelMaintainerTest` | Channel 管理测试 |
| 单元测试 | `OperationResultFutureTest` | 异步 Future 测试 |
| Handler 测试 | `SpaceHandlerTest` | 车位上报 Handler |
| Handler 测试 | `CarInHandlerTest` | 车辆入场 Handler |
| Handler 测试 | `CarOutHandlerTest` | 车辆出场 Handler |
| Handler 测试 | `DemoHandlerTest` | 演示 Handler |
| 集成测试 | `AuthIntegrationTest` | 认证集成测试 |
| 测试客户端 | `NettyClient` | TCP 客户端 |
| 测试客户端 | `ClientHandler` | 客户端消息处理 |
| 测试客户端 | `ClientAuthHandler` | 客户端认证处理 |

---

## 2. 架构设计

### 2.1 分层测试策略

```
┌─────────────────────────────────────────────────────────────┐
│                    测试层级金字塔                              │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│                      ╱╲                                     │
│                     ╱  ╲                                    │
│                    ╱ Docker ╲                               │
│                   ╱  测试     ╲                              │
│                  ╱────────────╲                             │
│                 ╱  完整测试     ╲                            │
│                ╱  (集成 + 覆盖率) ╲                           │
│               ╱──────────────────╲                          │
│              ╱    快速测试         ╲                         │
│             ╱    (单元测试)         ╲                        │
│            ╱────────────────────────╲                       │
│                                                             │
└─────────────────────────────────────────────────────────────┘

执行时机:
快速测试 → 开发中随时运行 (5-10 秒)
完整测试 → git commit 前运行 (30-60 秒)
Docker 测试 → 部署前运行 (1-2 分钟)
```

### 2.2 测试流程图

```
修改代码
    │
    ▼
┌─────────────────┐
│ 快速测试         │ ← 开发中随时执行
│ ./gradlew test  │
└────────┬────────┘
         │
         ▼
    测试通过？
         │
    ┌────┴────┐
    │         │
   是        否 → 修复代码
    │
    ▼
┌─────────────────┐
│ 完整测试         │ ← 提交前执行
│ ./scripts/      │
│ test-all.sh     │
└────────┬────────┘
         │
         ▼
    测试通过？
         │
    ┌────┴────┐
    │         │
   是        否 → 修复代码
    │
    ▼
   git commit
         │
         ▼
┌─────────────────┐
│ Docker 测试      │ ← 部署前执行
│ ./scripts/      │
│ docker-test.sh  │
└────────┬────────┘
         │
         ▼
    测试通过？
         │
    ┌────┴────┐
    │         │
   是        否 → 修复代码
    │
    ▼
  docker compose up -d
```

---

## 3. 组件设计

### 3.1 快速测试

**命令**: `./gradlew test`

**内容**:
- 所有单元测试
- 所有 Handler 测试
- 集成测试

**配置** (`build.gradle`):
```gradle
test {
    useJUnitPlatform()
    testLogging {
        events "passed", "skipped", "failed"
    }
}
```

**预期时间**: 5-10 秒

---

### 3.2 完整测试脚本

**文件**: `scripts/test-all.sh`

**内容**:
```bash
#!/bin/bash
set -e

cd "$(dirname "$0")/.."

echo "========================================"
echo "Running Full Test Suite"
echo "========================================"

# 1. 清理并运行测试
echo "[1/4] Running unit tests..."
./gradlew clean test

# 2. 代码覆盖率报告
echo "[2/4] Generating code coverage report..."
./gradlew jacocoTestReport

# 3. 代码风格检查
echo "[3/4] Running checkstyle..."
./gradlew checkstyleMain checkstyleTest

# 4. 构建验证
echo "[4/4] Verifying build..."
./gradlew build -x test

echo ""
echo "========================================"
echo "All tests passed!"
echo "========================================"
echo ""
echo "Test report: build/reports/tests/test/index.html"
echo "Coverage report: build/reports/jacoco/test/html/index.html"
```

**预期时间**: 30-60 秒

---

### 3.3 Docker 测试脚本

**文件**: `scripts/docker-test.sh`

**内容**:
```bash
#!/bin/bash
set -e

cd "$(dirname "$0")/.."

echo "========================================"
echo "Running Docker Test"
echo "========================================"

# 1. 构建测试镜像
echo "[1/4] Building test image..."
docker build -t parking-netty-framework:test .

# 2. 启动测试容器
echo "[2/4] Starting test container..."
docker run -d \
    --name test-container \
    -p 8080:8080 \
    -p 8889:8889 \
    -e SERVER_PORT=8080 \
    -e NETTY_TCP_PORT=8889 \
    parking-netty-framework:test

# 等待容器启动
echo "Waiting for container to start..."
sleep 10

# 3. 健康检查
echo "[3/4] Running health checks..."
echo "  - Checking Java version..."
docker exec test-container java -version

echo "  - Checking HTTP health..."
curl -s http://localhost:8080/actuator/health || {
    echo "Health check failed!"
    docker stop test-container
    docker rm test-container
    exit 1
}

echo "  - Checking TCP port..."
nc -zv localhost 8889 || {
    echo "TCP port check failed!"
    docker stop test-container
    docker rm test-container
    exit 1
}

# 4. 清理
echo "[4/4] Cleaning up..."
docker stop test-container
docker rm test-container

echo ""
echo "========================================"
echo "Docker test passed!"
echo "========================================"
```

**预期时间**: 1-2 分钟

---

### 3.4 Git Hooks

**文件**: `.git/hooks/pre-commit`

**内容**:
```bash
#!/bin/bash

echo "Running pre-commit tests..."

# 快速测试
./gradlew test --quiet || {
    echo "❌ Tests failed! Commit aborted."
    exit 1
}

echo "✅ Pre-commit tests passed!"
exit 0
```

**安装脚本**: `scripts/install-hooks.sh`

**内容**:
```bash
#!/bin/bash

cd "$(dirname "$0")/.."

# 备份现有 hooks
if [ -f .git/hooks/pre-commit ]; then
    cp .git/hooks/pre-commit .git/hooks/pre-commit.backup
fi

# 安装 pre-commit hook
cp scripts/hooks/pre-commit .git/hooks/pre-commit
chmod +x .git/hooks/pre-commit

echo "Git hooks installed successfully!"
echo ""
echo "Installed hooks:"
echo "  - pre-commit: Runs quick tests before commit"
echo ""
echo "To bypass pre-commit hook (not recommended):"
echo "  git commit --no-verify"
```

---

### 3.5 build.gradle 扩展

**添加插件和配置**:

```gradle
plugins {
    id 'org.springframework.boot' version '3.2.4'
    id 'io.spring.dependency-management' version '1.1.4'
    id 'java-library'
    id 'com.google.protobuf' version '0.9.4'
    id 'jacoco'           // 代码覆盖率
    id 'checkstyle'       // 代码风格
}

// JaCoCo 配置
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

// Checkstyle 配置
checkstyle {
    toolVersion = "10.12.5"
    configFile = file("${rootDir}/checkstyle.xml")
}

test {
    useJUnitPlatform()
    testLogging {
        events "passed", "skipped", "failed"
        showStandardStreams = true
    }
    finalizedBy jacocoTestReport
}
```

---

### 3.6 checkstyle.xml

**文件**: `checkstyle.xml`

**内容**:
```xml
<?xml version="1.0"?>
<!DOCTYPE module PUBLIC
    "-//Checkstyle//DTD Checkstyle Configuration 1.3//EN"
    "https://checkstyle.org/dtds/configuration_1_3.dtd">

<module name="Checker">
    <property name="charset" value="UTF-8"/>
    <property name="severity" value="warning"/>

    <module name="TreeWalker">
        <!-- 基础检查 -->
        <module name="ArrayTypeStyle"/>
        <module name="AvoidNestedBlocks"/>
        <module name="ConstantName"/>
        <module name="EmptyBlock"/>
        <module name="EmptyStatement"/>
        <module name="EqualsHashCode"/>
        <module name="FinalLocalVariable"/>
        <module name="HiddenField">
            <property name="ignoreConstructorParameter" value="true"/>
        </module>
        <module name="IllegalImport"/>
        <module name="InnerAssignment"/>
        <module name="LocalFinalVariableName"/>
        <module name="LocalVariableName"/>
        <module name="MemberName"/>
        <module name="MethodName"/>
        <module name="ModifierOrder"/>
        <module name="NeedBraces"/>
        <module name="PackageName"/>
        <module name="ParameterName"/>
        <module name="RedundantImport"/>
        <module name="SimplifyBooleanExpression"/>
        <module name="SimplifyBooleanReturn"/>
        <module name="StaticVariableName"/>
        <module name="TypeName"/>
        <module name="UnusedImports"/>
        
        <!-- 代码复杂度限制 -->
        <module name="MethodLength">
            <property name="max" value="100"/>
        </module>
        <module name="ParameterNumber">
            <property name="max" value="8"/>
        </module>
    </module>

    <!-- 文件长度限制 -->
    <module name="FileLength">
        <property name="max" value="500"/>
    </module>

    <!-- 行长度限制 -->
    <module name="LineLength">
        <property name="max" value="120"/>
    </module>
</module>
```

---

## 4. 目录结构

```
parking-netty-framework/
├── scripts/
│   ├── test-all.sh              # 完整测试脚本
│   ├── docker-test.sh           # Docker 测试脚本
│   ├── install-hooks.sh         # Git Hooks 安装脚本
│   └── hooks/
│       └── pre-commit           # pre-commit hook
├── checkstyle.xml               # Checkstyle 配置
├── build.gradle                 # 添加 JaCoCo, Checkstyle 插件
└── .git/hooks/
    └── pre-commit               # 安装后创建
```

---

## 5. 使用指南

### 5.1 日常开发

```bash
# 随时运行快速测试
./gradlew test

# 查看测试报告
open build/reports/tests/test/index.html
```

### 5.2 提交前

```bash
# 运行完整测试套件
./scripts/test-all.sh

# 或安装 git hooks 后自动运行
git commit -m "feat: xxx"
```

### 5.3 部署前

```bash
# 运行 Docker 测试
./scripts/docker-test.sh

# 构建并部署
docker compose build
docker compose up -d
```

### 5.4 安装 Git Hooks

```bash
# 安装 pre-commit hook
./scripts/install-hooks.sh

# 查看已安装的 hooks
ls -la .git/hooks/
```

---

## 6. 预期输出

### 6.1 快速测试输出

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

BUILD SUCCESSFUL in 8s
```

### 6.2 完整测试输出

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
```

### 6.3 Docker 测试输出

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

## 7. 待创建/修改文件清单

| 文件 | 操作 | 说明 |
|------|------|------|
| `scripts/test-all.sh` | 创建 | 完整测试脚本 |
| `scripts/docker-test.sh` | 创建 | Docker 测试脚本 |
| `scripts/install-hooks.sh` | 创建 | Git Hooks 安装脚本 |
| `scripts/hooks/pre-commit` | 创建 | pre-commit hook |
| `checkstyle.xml` | 创建 | Checkstyle 配置 |
| `build.gradle` | 修改 | 添加 JaCoCo, Checkstyle 插件 |
| `.git/hooks/pre-commit` | 安装后创建 | Git hook |

---

## 8. 后续扩展建议

1. **CI/CD 集成** - 未来如果托管到 GitHub/GitLab，可直接使用 GitHub Actions/GitLab CI
2. **测试覆盖率门槛** - 设置最低覆盖率要求（如 80%）
3. **性能测试** - 添加 JMH 基准测试
4. **集成测试扩展** - 添加更多端到端测试场景

---
