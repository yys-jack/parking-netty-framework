#!/bin/bash
set -e

cd "$(dirname "$0")/.."

# 清理函数
cleanup() {
    echo "Cleaning up..."
    docker stop test-container 2>/dev/null || true
    docker rm test-container 2>/dev/null || true
}
trap cleanup EXIT

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

# 轮询等待应用启动
echo "Waiting for container to start..."
for i in {1..30}; do
    if curl -s http://localhost:8080/actuator/health > /dev/null 2>&1; then
        echo "Application started successfully."
        break
    fi
    if [ "$i" -eq 30 ]; then
        echo "Application failed to start within 60 seconds."
        exit 1
    fi
    sleep 2
done

# 3. 健康检查
echo "[3/4] Running health checks..."
echo "  - Checking Java version..."
docker exec test-container java -version

echo "  - Checking HTTP health..."
if ! curl -s http://localhost:8080/actuator/health; then
    echo "Health check failed!"
    exit 1
fi

echo "  - Checking TCP port..."
if ! nc -zv localhost 8889; then
    echo "TCP port check failed!"
    exit 1
fi

# 4. 清理（trap 会自动执行）
echo ""
echo "========================================"
echo "Docker test passed!"
echo "========================================"
