# Dockerfile 重构设计文档

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 将 Dockerfile 从完整 JDK 重构为 JRE 运行时 + 安全加固，镜像大小减少 50% 以上

**Architecture:** 两阶段构建 + JRE 运行时 + 非 root 用户 + 健康检查

**Tech Stack:** Dragonwell 21 JRE, Docker 多阶段构建

---

## 1. 架构设计

### 1.1 整体架构

```
┌─────────────────────────────────────────────────────────┐
│                   阶段 1: 构建镜像                       │
│  FROM debian:bookworm-slim AS build                     │
│  - 复制完整 JDK（用于 jlink 裁剪）                        │
│  - 复制应用 JAR                                         │
│  - 使用 jlink 创建自定义 JRE 运行时                       │
└─────────────────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────┐
│                   阶段 2: 运行镜像                       │
│  FROM debian:bookworm-slim                              │
│  - 复制自定义 JRE（约 150MB）                            │
│  - 复制应用 JAR                                         │
│  - 创建非 root 用户                                     │
│  - 配置健康检查                                         │
│  - 设置 JVM 容器感知参数                                 │
└─────────────────────────────────────────────────────────┘
```

### 1.2 组件职责

| 组件 | 职责 | 当前 | 重构后 |
|------|------|------|--------|
| **基础镜像** | 操作系统层 | debian:bookworm-slim | debian:bookworm-slim |
| **JDK/JRE** | Java 运行时 | 完整 JDK (378MB) | 裁剪 JRE (~150MB) |
| **运行用户** | 安全性 | root | appuser（非 root） |
| **健康检查** | 可用性检测 | 无 | /actuator/health |
| **JVM 参数** | 容器适配 | 无 | 容器感知参数 |

---

## 2. 详细设计

### 2.1 阶段 1：构建自定义 JRE

```dockerfile
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
```

**说明**：
- `--add-modules`: 添加应用需要的模块
- `--compress=2`: 压缩字符串，减少大小
- `--no-header-files`: 不复制头文件
- `--no-man-pages`: 不复制手册页
- `--strip-debug`: 移除调试信息

### 2.2 阶段 2：生产运行镜像

```dockerfile
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

---

## 3. 配置设计

### 3.1 JVM 参数说明

| 参数 | 说明 | 值 |
|------|------|-----|
| `UseContainerSupport` | 启用容器感知 | 启用 |
| `InitialRAMPercentage` | 初始堆大小百分比 | 50% |
| `MaxRAMPercentage` | 最大堆大小百分比 | 75% |
| `UseG1GC` | 使用 G1 垃圾回收器 | 启用 |
| `MaxGCPauseMillis` | 最大 GC 停顿时间 | 200ms |
| `HeapDumpOnOutOfMemoryError` | OOM 时生成堆转储 | 启用 |

### 3.2 健康检查配置

| 参数 | 说明 | 值 |
|------|------|-----|
| `interval` | 检查间隔 | 30s |
| `timeout` | 超时时间 | 10s |
| `start-period` | 启动宽限期 | 40s |
| `retries` | 重试次数 | 3 |

---

## 4. 预期效果

### 4.1 镜像大小对比

| 组件 | 重构前 | 重构后 | 减少 |
|------|--------|--------|------|
| JDK/JRE | 378MB | ~150MB | 60% |
| 基础镜像 | ~150MB | ~150MB | - |
| 应用 JAR | ~50MB | ~50MB | - |
| **总计** | **~700MB** | **~350MB** | **50%** |

### 4.2 安全性提升

| 项目 | 重构前 | 重构后 |
|------|--------|--------|
| 运行用户 | root | appuser（非 root） |
| 文件系统 | 可写 | /app 只读（除日志外） |
| 健康检查 | 无 | 有（/actuator/health） |

### 4.3 性能提升

| 项目 | 重构前 | 重构后 |
|------|--------|--------|
| JVM 启动 | 标准 | 容器感知 + G1GC |
| GC 停顿 | 不确定 | Max 200ms |
| OOM 处理 | 无 | 自动 HeapDump |

---

## 5. 文件清单

| 文件 | 操作 | 说明 |
|------|------|------|
| `Dockerfile` | 修改 | 重构为两阶段构建 |
| `docker-compose.yml` | 可选修改 | 适配新的健康检查配置 |
| `docs/DOCKER_DEPLOYMENT.md` | 更新 | 更新部署文档 |

---

## 6. 测试策略

### 6.1 构建测试

```bash
# 构建镜像
docker build -t parking-netty-framework:test .

# 验证镜像大小
docker images parking-netty-framework:test

# 验证 JRE 版本
docker run --rm parking-netty-framework:test java -version
```

### 6.2 功能测试

```bash
# 启动容器
docker run -d --name test-app \
    -p 8080:8080 -p 8889:8889 \
    parking-netty-framework:test

# 验证健康检查
curl http://localhost:8081/actuator/health

# 验证 TCP 服务
telnet localhost 8889

# 验证非 root 用户
docker exec test-app whoami
# 应输出：appuser
```

### 6.3 集成测试

```bash
# 使用 docker-compose 启动
docker compose up -d

# 验证负载均衡
curl http://localhost:8889/actuator/health

# 清理
docker compose down
```

---

## 7. 验收标准

- [ ] 镜像大小减少 50% 以上（从 ~700MB 降至 ~350MB）
- [ ] 应用能正常启动和运行
- [ ] 健康检查正常工作
- [ ] 容器以非 root 用户运行
- [ ] JVM 参数正确配置（容器感知）
- [ ] 所有现有测试通过
- [ ] docker-compose 部署正常

---

## 8. 风险与缓解

| 风险 | 影响 | 缓解措施 |
|------|------|----------|
| jlink 裁剪过度 | 缺少必要模块 | 测试覆盖所有功能 |
| Alpine 兼容性 | musl libc 问题 | 使用 debian:bookworm-slim |
| 非 root 用户权限 | 日志写入失败 | 正确设置目录权限 |

---

## 9. 实施计划

1. 修改 Dockerfile 为两阶段构建
2. 使用 jlink 创建自定义 JRE
3. 添加非 root 用户配置
4. 添加健康检查
5. 添加 JVM 容器感知参数
6. 测试镜像构建和运行
7. 验证镜像大小和功能
8. 更新部署文档
