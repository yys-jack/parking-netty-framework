# Dockerfile 重构实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 Dockerfile 从完整 JDK 重构为 JRE 运行时 + 安全加固，镜像大小减少 50% 以上

**Architecture:** 两阶段构建 + jlink 裁剪 JRE + 非 root 用户 + 健康检查 + JVM 容器感知参数

**Tech Stack:** Dragonwell 21, Docker 多阶段构建，jlink 模块裁剪

---

### Task 1: 重构 Dockerfile 为两阶段构建

**Files:**
- Modify: `Dockerfile`

- [ ] **Step 1: 编写新的 Dockerfile**

```dockerfile
# 阶段 1: 构建自定义 JRE
FROM debian:bookworm-slim AS build

ENV JAVA_HOME=/opt/jdk
ENV PATH=$JAVA_HOME/bin:$PATH

# 复制完整 JDK（用于 jlink 裁剪）
COPY dragonwell-21/ /opt/jdk/

# 复制应用 JAR
COPY build/libs/parking-netty-framework-1.0.0.jar /app/app.jar

# 使用 jlink 创建最小 JRE
RUN /opt/jdk/bin/jlink \
    --add-modules java.base,java.logging,java.net.http,java.management,java.sql,java.naming \
    --output /opt/jre-custom \
    --compress=2 \
    --no-header-files \
    --no-man-pages \
    --strip-debug

# 阶段 2: 生产运行镜像
FROM debian:bookworm-slim

# 创建非 root 用户
RUN groupadd -r appgroup && useradd -r -g appgroup appuser

# 从构建阶段复制自定义 JRE
COPY --from=build /opt/jre-custom /opt/jre

# 复制应用 JAR
COPY --from=build /app/app.jar /app/app.jar

# 设置环境变量
ENV JAVA_HOME=/opt/jre
ENV PATH=$JAVA_HOME/bin:$PATH
ENV SERVER_PORT=8080
ENV NETTY_TCP_PORT=8889

# JVM 容器感知参数
ENV JAVA_OPTS="-XX:+UseContainerSupport \
               -XX:InitialRAMPercentage=50.0 \
               -XX:MaxRAMPercentage=75.0 \
               -XX:+UseG1GC \
               -XX:MaxGCPauseMillis=200 \
               -XX:+HeapDumpOnOutOfMemoryError \
               -XX:HeapDumpPath=/app/logs/heap_dump.hprof"

# 设置工作目录和权限
WORKDIR /app
RUN chown -R appuser:appgroup /app

# 切换到非 root 用户
USER appuser

# 健康检查
HEALTHCHECK --interval=30s --timeout=10s --start-period=40s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:${MANAGEMENT_SERVER_PORT:-8081}/actuator/health || exit 1

# 暴露端口
EXPOSE ${SERVER_PORT} ${NETTY_TCP_PORT}

# 启动应用
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
```

- [ ] **Step 2: 构建镜像验证**

```bash
docker build -t parking-netty-framework:test .
```

Expected: BUILD SUCCESS

- [ ] **Step 3: 验证镜像大小**

```bash
docker images parking-netty-framework:test
```

Expected: 镜像大小约 350MB（重构前约 700MB）

- [ ] **Step 4: 验证 JRE 版本**

```bash
docker run --rm parking-netty-framework:test java -version
```

Expected: 显示 Dragonwell 21 版本信息

- [ ] **Step 5: 提交**

```bash
git add Dockerfile
git commit -m "feat: refactor Dockerfile with two-stage build and jlink JRE"
```

---

### Task 2: 验证容器运行和非 root 用户

**Files:**
- N/A（手动测试）

- [ ] **Step 1: 启动测试容器**

```bash
docker run -d --name test-app \
    -p 8080:8080 -p 8889:8889 -p 8081:8081 \
    -e MANAGEMENT_SERVER_PORT=8081 \
    parking-netty-framework:test
```

- [ ] **Step 2: 验证容器日志**

```bash
docker logs -f test-app
```

Expected: 应用正常启动，无错误

- [ ] **Step 3: 验证非 root 用户**

```bash
docker exec test-app whoami
```

Expected: 输出 `appuser`

- [ ] **Step 4: 验证健康检查**

```bash
docker inspect --format='{{.State.Health.Status}}' test-app
```

Expected: `healthy`（等待 1-2 分钟）

- [ ] **Step 5: 验证 HTTP 健康端点**

```bash
curl http://localhost:8081/actuator/health
```

Expected: `{"status":"UP"}`

- [ ] **Step 6: 验证 TCP 服务**

```bash
echo "test" | nc localhost 8889
```

Expected: TCP 服务正常响应

- [ ] **Step 7: 清理测试容器**

```bash
docker stop test-app && docker rm test-app
```

---

### Task 3: 更新 docker-compose.yml 适配健康检查

**Files:**
- Modify: `docker-compose.yml`

- [ ] **Step 1: 更新 docker-compose.yml**

```yaml
version: '3.8'

services:
  nginx-lb:
    image: nginx:alpine
    container_name: nginx-lb
    ports:
      - "8889:8889"
    volumes:
      - ./nginx/nginx.conf:/etc/nginx/nginx.conf:ro
    depends_on:
      - netty-server-1
      - netty-server-2
    networks:
      - netty-network
    restart: unless-stopped

  netty-server-1:
    build:
      context: .
      dockerfile: Dockerfile
    container_name: netty-server-1
    ports:
      - "8081:8081"
    environment:
      - SERVER_PORT=8080
      - NETTY_TCP_PORT=8890
      - MANAGEMENT_SERVER_PORT=8081
      - SPRING_PROFILES_ACTIVE=docker
    volumes:
      - ./logs/server1:/app/logs
    networks:
      - netty-network
    restart: unless-stopped
    healthcheck:
      test: ["CMD", "wget", "--no-verbose", "--tries=1", "--spider", "http://localhost:8081/actuator/health"]
      interval: 30s
      timeout: 10s
      retries: 3
      start_period: 40s

  netty-server-2:
    build:
      context: .
      dockerfile: Dockerfile
    container_name: netty-server-2
    ports:
      - "8082:8081"
    environment:
      - SERVER_PORT=8080
      - NETTY_TCP_PORT=8891
      - MANAGEMENT_SERVER_PORT=8081
      - SPRING_PROFILES_ACTIVE=docker
    volumes:
      - ./logs/server2:/app/logs
    networks:
      - netty-network
    restart: unless-stopped
    healthcheck:
      test: ["CMD", "wget", "--no-verbose", "--tries=1", "--spider", "http://localhost:8081/actuator/health"]
      interval: 30s
      timeout: 10s
      retries: 3
      start_period: 40s

networks:
  netty-network:
    driver: bridge
```

- [ ] **Step 2: 测试 docker-compose 构建**

```bash
docker compose build
```

Expected: BUILD SUCCESS

- [ ] **Step 3: 启动服务验证**

```bash
docker compose up -d
```

- [ ] **Step 4: 验证服务健康状态**

```bash
docker compose ps
```

Expected: 所有服务状态为 `healthy`

- [ ] **Step 5: 验证负载均衡**

```bash
curl http://localhost:8889/actuator/health
```

Expected: `{"status":"UP"}`

- [ ] **Step 6: 清理**

```bash
docker compose down
```

- [ ] **Step 7: 提交**

```bash
git add docker-compose.yml
git commit -m "feat: add healthcheck configuration to docker-compose"
```

---

### Task 4: 测试镜像大小和性能

**Files:**
- N/A（测试验证）

- [ ] **Step 1: 构建最终镜像**

```bash
docker build -t parking-netty-framework:final .
```

- [ ] **Step 2: 记录镜像大小**

```bash
docker images parking-netty-framework:final --format "{{.Size}}"
```

Expected: 约 350MB（减少 50% 以上）

- [ ] **Step 3: 对比旧镜像（如果存在）**

```bash
docker images | grep parking-netty-framework
```

- [ ] **Step 4: 验证启动时间**

```bash
time docker run --rm parking-netty-framework:final java $JAVA_OPTS -jar app.jar
```

Expected: 启动时间正常（< 30 秒）

---

### Task 5: 更新部署文档

**Files:**
- Modify: `docs/DOCKER_DEPLOYMENT.md`

- [ ] **Step 1: 读取现有文档**

```bash
cat docs/DOCKER_DEPLOYMENT.md
```

- [ ] **Step 2: 更新 Dockerfile 说明部分**

在文档中添加新的 Dockerfile 说明：

```markdown
### Dockerfile 说明

**重构后的 Dockerfile 特性**：

1. **两阶段构建** - 阶段 1 使用 jlink 裁剪自定义 JRE，阶段 2 只复制运行时
2. **镜像大小优化** - 从 ~700MB 降至 ~350MB（减少 50%）
3. **安全性提升** - 使用非 root 用户（appuser）运行应用
4. **健康检查** - 每 30 秒检测 /actuator/health 端点
5. **JVM 容器感知** - 自动适配容器内存限制

**JVM 参数配置**：

| 参数 | 说明 | 值 |
|------|------|-----|
| `UseContainerSupport` | 启用容器感知 | 启用 |
| `InitialRAMPercentage` | 初始堆大小 | 50% |
| `MaxRAMPercentage` | 最大堆大小 | 75% |
| `UseG1GC` | G1 垃圾回收器 | 启用 |
| `MaxGCPauseMillis` | 最大 GC 停顿 | 200ms |
```

- [ ] **Step 3: 添加健康检查说明**

```markdown
### 健康检查配置

Docker 镜像内置健康检查，配置如下：

- **检查端点**: `/actuator/health`
- **检查间隔**: 30 秒
- **超时时间**: 10 秒
- **重试次数**: 3 次
- **启动宽限**: 40 秒

**查看健康状态**：

```bash
docker inspect --format='{{.State.Health.Status}}' <container-name>
```
```

- [ ] **Step 4: 提交**

```bash
git add docs/DOCKER_DEPLOYMENT.md
git commit -m "docs: update DOCKER_DEPLOYMENT.md with new Dockerfile features"
```

---

### Task 6: 运行全量测试并验证

**Files:**
- N/A

- [ ] **Step 1: 运行项目测试**

```bash
./gradlew clean test
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 2: 构建 Docker 镜像**

```bash
docker build -t parking-netty-framework:latest .
```

- [ ] **Step 3: 使用 docker-compose 启动**

```bash
docker compose up -d
```

- [ ] **Step 4: 等待服务启动**

```bash
sleep 30
```

- [ ] **Step 5: 验证所有服务健康**

```bash
docker compose ps
```

Expected: 所有服务状态为 `healthy`

- [ ] **Step 6: 验证 API 端点**

```bash
curl http://localhost:8081/actuator/health
curl http://localhost:8082/actuator/health
```

Expected: 返回 `{"status":"UP"}`

- [ ] **Step 7: 验证 TCP 服务**

```bash
echo "test" | nc localhost 8889
```

- [ ] **Step 8: 查看镜像大小**

```bash
docker images parking-netty-framework:latest
```

Expected: 约 350MB

- [ ] **Step 9: 清理**

```bash
docker compose down
```

---

## 验收标准

完成所有任务后验证：

- [ ] 镜像大小减少 50% 以上（从 ~700MB 降至 ~350MB）
- [ ] 应用能正常启动和运行
- [ ] 健康检查正常工作（状态为 healthy）
- [ ] 容器以非 root 用户运行（whoami 输出 appuser）
- [ ] JVM 参数正确配置（容器感知）
- [ ] docker-compose 部署正常
- [ ] 所有 6 个任务完成并提交

---

## 文件清单

| 文件 | 操作 | 说明 |
|------|------|------|
| `Dockerfile` | 修改 | 重构为两阶段构建 + jlink + 安全加固 |
| `docker-compose.yml` | 修改 | 添加健康检查配置 |
| `docs/DOCKER_DEPLOYMENT.md` | 修改 | 更新部署文档说明 |
