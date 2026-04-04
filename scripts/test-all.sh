#!/bin/bash
set -e

# 设置 Java 21 环境
export JAVA_HOME=/home/yy/java/dragonwell-21.0.10
export PATH=$JAVA_HOME/bin:$PATH

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
