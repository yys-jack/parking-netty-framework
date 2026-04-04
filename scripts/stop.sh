#!/bin/bash
set -e

echo "=== Netty 分布式部署停止脚本 ==="

# 停止所有服务
docker compose down

echo "=== 停止完成 ==="
