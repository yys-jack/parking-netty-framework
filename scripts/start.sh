#!/bin/bash
set -e

echo "=== Netty 分布式部署启动脚本 ==="

# 1. 构建项目
echo "[1/4] 构建项目..."
./gradlew clean build -x test

# 2. 创建日志目录
echo "[2/4] 创建日志目录..."
mkdir -p logs/server1 logs/server2

# 3. 确保 nginx 目录存在
echo "[3/4] 检查 Nginx 配置..."
if [ ! -f nginx/nginx.conf ]; then
    echo "错误：nginx/nginx.conf 不存在"
    exit 1
fi

# 4. 启动服务
echo "[4/4] 启动 Docker 服务..."
docker compose up -d

echo ""
echo "=== 启动完成 ==="
echo "查看状态：docker compose ps"
echo "查看日志：docker compose logs -f"
