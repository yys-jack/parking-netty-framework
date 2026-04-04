#!/bin/bash
set -e

cd "$(dirname "$0")/.."

echo "Building application image with JDK 21..."
docker build -t parking-netty-framework:app .

echo ""
echo "Docker build completed!"
echo ""
docker images | grep parking-netty-framework
