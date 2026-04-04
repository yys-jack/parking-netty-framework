统一#!/bin/bash
# 运行客户端测试脚本

cd "$(dirname "$0")"

# 获取 gradle 依赖 classpath
CLASSPATH=$(./gradlew -q dependencies --configuration testRuntimeClasspath --console plain 2>/dev/null | \
  grep -E '^\\---|\\+---' | \
  awk -F' ' '{print $2}' | \
  grep -E '^[a-zA-Z].*:' | \
  while read line; do
    group=$(echo "$line" | cut -d':' -f1)
    artifact=$(echo "$line" | cut -d':' -f2)
    version=$(echo "$line" | cut -d':' -f3)
    jar_name="${artifact}-${version}.jar"
    find ~/.gradle/caches/modules-2/files-2.1 -name "$jar_name" 2>/dev/null | head -1
  done | tr '\n' ':'
)

# 添加 build 目录
CLASSPATH="${CLASSPATH}build/classes/java/main:build/classes/java/test:build/resources/main"

echo "Starting Netty Client..."
echo "Server: ${1:-localhost}:${2:-8889}"
echo "Serial: ${3:-TEST-001}"

java -cp "$CLASSPATH" cn.tnar.parking.client.NettyClient \
     -Dclient.host="${1:-localhost}" \
     -Dclient.port="${2:-8889}" \
     -Dclient.serial="${3:-TEST-001}" \
     -Dclient.secret="${4:-test-secret}"
