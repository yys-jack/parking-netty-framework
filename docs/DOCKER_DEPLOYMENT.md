# Docker 部署指南

**日期**: 2026-04-04  
**状态**: 生产就绪

---

## 1. 概述

本项目使用 Docker 进行容器化部署，支持多实例水平扩展和 Nginx 负载均衡。

### 架构

```
                    ┌─────────────────┐
                    │   Port 8889     │
                    │   Nginx LB      │
                    │  (least_conn)   │
                    └────────┬────────┘
                             │
            ┌────────────────┼────────────────┐
            │                │                │
            ▼                ▼                ▼
    ┌───────────────┐ ┌───────────────┐
    │ netty-server-1│ │ netty-server-2│
    │   Port 8890   │ │   Port 8891   │
    │   JDK 21      │ │   JDK 21      │
    └───────────────┘ └───────────────┘
```

---

## 2. 快速开始

### 2.1 环境要求

| 软件 | 版本 | 说明 |
|------|------|------|
| Docker | 20+ | 容器运行时 |
| Docker Compose | 2.x | 容器编排 |
| JDK | 21 | 本地开发环境 |

### 2.2 启动

```bash
# 方式一：使用启动脚本（推荐）
./scripts/start.sh

# 方式二：手动启动
docker compose build
docker compose up -d
```

### 2.3 查看状态

```bash
# 查看容器状态
docker compose ps

# 查看日志
docker compose logs -f

# 查看单个容器日志
docker logs netty-server-1
```

### 2.4 停止

```bash
# 方式一：使用停止脚本
./scripts/stop.sh

# 方式二：手动停止
docker compose down
```

---

## 3. Docker 镜像构建

### 3.1 自定义 JDK 镜像

本项目使用 **Alibaba Dragonwell 21.0.10** 构建自定义基础镜像，确保与本地开发环境一致。

**JDK 下载**:
```bash
# 下载 JDK 到项目根目录
mkdir -p dragonwell-21
cd dragonwell-21
wget https://github.com/alibaba/dragonwell/releases/download/dragonwell-21.0.10/...
tar -xzf dragonwell-21.0.10.tar.gz
```

### 3.2 Dockerfile 说明

**重构后的 Dockerfile 特性**：

1. **两阶段构建** - 阶段 1 使用 jlink 裁剪自定义 JRE，阶段 2 只复制运行时
2. **镜像大小优化** - 从 ~505MB 降至 ~272MB（减少 46%）
3. **安全性提升** - 使用非 root 用户（appuser）运行应用
4. **健康检查** - 每 30 秒检测 /actuator/health 端点
5. **JVM 容器感知** - 自动适配容器内存限制
6. **时区支持** - 配置 Asia/Shanghai 时区

**JVM 参数配置**：

| 参数 | 说明 | 值 |
|------|------|-----|
| `UseContainerSupport` | 启用容器感知 | 启用 |
| `InitialRAMPercentage` | 初始堆大小 | 50% |
| `MaxRAMPercentage` | 最大堆大小 | 75% |
| `UseG1GC` | G1 垃圾回收器 | 启用 |
| `MaxGCPauseMillis` | 最大 GC 停顿 | 200ms |
| `HeapDumpOnOutOfMemoryError` | OOM 时生成堆转储 | 启用 |

**jlink 模块裁剪**：

```dockerfile
RUN /opt/jdk/bin/jlink \
    --add-modules java.base,java.logging,java.net.http,java.management,java.sql,java.naming,java.desktop,java.datatransfer,java.security.jgss,java.instrument \
    --output /opt/jre-custom \
    --no-header-files \
    --no-man-pages \
    --strip-debug
```

### 3.3 构建镜像

```bash
# 方式一：使用构建脚本（推荐）
./scripts/build-docker.sh

# 方式二：手动构建
docker build -t parking-netty-framework:test .
```

### 3.4 镜像信息

```bash
docker images parking-netty-framework
```

| 镜像 | 大小 | 说明 |
|------|------|------|
| `parking-netty-framework:test` | ~272MB | 重构后应用镜像 |
| `parking-netty-framework:app` | ~505MB | 重构前应用镜像 |

---

## 4. Docker Compose 配置

### 4.1 配置文件

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
    environment:
      - SERVER_PORT=8080
      - NETTY_TCP_PORT=8890
      - SPRING_PROFILES_ACTIVE=docker
    volumes:
      - ./logs/server1:/app/logs
    networks:
      - netty-network
    restart: unless-stopped

  netty-server-2:
    build:
      context: .
      dockerfile: Dockerfile
    container_name: netty-server-2
    environment:
      - SERVER_PORT=8080
      - NETTY_TCP_PORT=8891
      - SPRING_PROFILES_ACTIVE=docker
    volumes:
      - ./logs/server2:/app/logs
    networks:
      - netty-network
    restart: unless-stopped

networks:
  netty-network:
    driver: bridge
```

### 4.2 环境说明

| 服务 | 容器名 | 端口 | 说明 |
|------|--------|------|------|
| Nginx | nginx-lb | 8889 | TCP 负载均衡器 |
| Netty-1 | netty-server-1 | 8890 | Netty 实例 1 |
| Netty-2 | netty-server-2 | 8891 | Netty 实例 2 |

---

## 5. 监控部署

### 5.1 独立监控模式

使用 `docker-compose.monitoring.yml` 部署 Prometheus 和 Grafana:

```bash
# 启动监控栈
docker compose -f docker-compose.monitoring.yml up -d

# 查看状态
docker compose -f docker-compose.monitoring.yml ps
```

### 5.2 访问地址

| 服务 | 地址 | 说明 |
|------|------|------|
| Prometheus | http://localhost:9090 | 指标查询和告警管理 |
| Grafana | http://localhost:3000 | 仪表板展示 (admin/admin) |

### 5.3 配置说明

**prometheus.yml**:
- 抓取间隔：15 秒
- 目标：`host.docker.internal:8081`
- 端点：`/actuator/prometheus`

**告警规则**:
- 高连接数（>1000 警告，>5000 严重）
- 高认证失败率（>10%）
- 高消息延迟（P99 >500ms 警告，>1s 严重）
- 应用下线

---

## 6. Nginx 配置

### 5.1 配置文件

```nginx
events {
    worker_connections 1024;
}

stream {
    upstream netty_servers {
        least_conn;
        # 活动节点
        server netty-server-1:8890 max_fails=3 fail_timeout=30s;
        server netty-server-2:8891 max_fails=3 fail_timeout=30s;
    }

    server {
        listen 8889;
        proxy_pass netty_servers;
        proxy_connect_timeout 10s;
        proxy_read_timeout 30s;
    }
}
```

### 5.2 负载均衡策略

- **least_conn**: 最少连接优先
- **max_fails**: 最大失败次数 3 次
- **fail_timeout**: 失败超时 30 秒

### 5.3 添加新节点

1. 修改 `nginx/nginx.conf`，添加新节点：

```nginx
upstream netty_servers {
    least_conn;
    server netty-server-1:8890 max_fails=3 fail_timeout=30s;
    server netty-server-2:8891 max_fails=3 fail_timeout=30s;
    server netty-server-3:8892 max_fails=3 fail_timeout=30s;  # 新增
}
```

2. 修改 `docker-compose.yml`，添加新服务：

```yaml
netty-server-3:
  build:
    context: .
    dockerfile: Dockerfile
  container_name: netty-server-3
  environment:
    - SERVER_PORT=8080
    - NETTY_TCP_PORT=8892
    - SPRING_PROFILES_ACTIVE=docker
  volumes:
    - ./logs/server3:/app/logs
  networks:
    - netty-network
  restart: unless-stopped
```

3. 重新加载配置：

```bash
docker compose up -d
```

---

## 6. 扩缩容

### 6.1 扩容（添加实例）

```bash
# 编辑 docker-compose.yml 添加新实例

# 重新部署
docker compose up -d

# 验证
docker compose ps
```

### 6.2 缩容（减少实例）

```bash
# 编辑 docker-compose.yml 移除实例
# 编辑 nginx/nginx.conf 移除对应节点

# 停止并移除容器
docker compose down
docker compose up -d

# 验证
docker compose ps
```

---

## 7. 故障处理

### 7.1 查看日志

```bash
# 所有服务
docker compose logs -f

# 单个服务
docker logs netty-server-1
docker logs nginx-lb

# 实时查看
docker compose logs -f netty-server-1
```

### 7.2 重启服务

```bash
# 重启单个容器
docker compose restart netty-server-1

# 重启所有容器
docker compose restart
```

### 7.3 重建镜像

```bash
# 清理缓存并重建
docker compose build --no-cache
docker compose up -d
```

### 7.4 进入容器

```bash
# 进入容器调试
docker exec -it netty-server-1 bash

# 查看应用日志
tail -f /app/logs/app.log

# 检查 Java 进程
ps aux | grep java

# 检查端口
netstat -tlnp | grep 8890
```

### 7.5 清理资源

```bash
# 停止并移除所有容器
docker compose down

# 删除镜像
docker rmi parking-netty-framework:app
docker rmi parking-netty-framework:jdk-21

# 清理网络
docker network prune
```

---

## 8. 健康检查

### 8.1 Docker 健康检查

Docker 镜像内置健康检查，配置如下：

- **检查端点**: `/actuator/health`
- **检查间隔**: 30 秒
- **超时时间**: 10 秒
- **重试次数**: 3 次
- **启动宽限**: 60 秒

**查看健康状态**：

```bash
docker inspect --format='{{.State.Health.Status}}' <container-name>
```

**查看健康检查日志**：

```bash
docker inspect --format='{{range .State.Health.Log}}{{.Output}}{{end}}' <container-name>
```

### 8.2 docker-compose 健康检查

`docker-compose.yml` 中为每个 netty-server 实例配置了健康检查：

```yaml
netty-server-1:
  healthcheck:
    test: ["CMD-SHELL", "wget -q --server-response --timeout=5 http://localhost:8081/actuator/health 2>&1 | grep -q 'HTTP' || exit 1"]
    interval: 30s
    timeout: 10s
    retries: 3
    start_period: 60s
```

**查看服务健康状态**：

```bash
docker compose ps
```

预期输出：
```
NAME             STATUS
netty-server-1   Up About a minute (healthy)
netty-server-2   Up About a minute (healthy)
nginx-lb         Up About a minute
```

### 8.3 HTTP 健康检查

```bash
# 检查 Actuator 端点
curl http://localhost:8080/actuator/health

# 预期输出（应用正常运行，但 Redis 未连接时）
{"status":"DOWN"}

# 注意：503 响应码表示应用正常运行，只是部分依赖（如 Redis）未就绪
```

### 8.4 TCP 健康检查

```bash
# 检查 TCP 端口
nc -zv localhost 8889

# 预期输出
Connection succeeded
```

### 8.5 Docker 测试脚本

```bash
# 运行完整 Docker 测试
./scripts/docker-test.sh
```

测试内容:
1. 构建测试镜像
2. 启动测试容器
3. 健康检查（Java 版本、HTTP 端点、TCP 端口）
4. 清理容器

---

## 9. 生产环境配置

### 9.1 JVM 参数

```yaml
netty-server-1:
  environment:
    - JAVA_OPTS=-Xms1g -Xmx2g -XX:+UseG1GC -XX:MaxGCPauseMillis=200
```

### 9.2 日志配置

```yaml
netty-server-1:
  volumes:
    - /var/log/parking-netty/server1:/app/logs
```

### 9.3 监控

```yaml
# 添加 Prometheus 端点暴露
management:
  endpoints:
    web:
      exposure:
        include: health,info,prometheus
```

---

## 10. 部署脚本

### 10.1 start.sh

```bash
#!/bin/bash
set -e

cd "$(dirname "$0")/.."

echo "Building Docker image..."
docker build -t parking-netty-framework:app .

echo "Starting services..."
docker compose up -d

echo "Waiting for services to start..."
sleep 10

echo "Checking health..."
curl -s http://localhost:8080/actuator/health

echo ""
echo "Deployment complete!"
docker compose ps
```

### 10.2 stop.sh

```bash
#!/bin/bash

cd "$(dirname "$0")/.."

echo "Stopping services..."
docker compose down

echo "Services stopped."
```

### 10.3 build-docker.sh

```bash
#!/bin/bash
set -e

cd "$(dirname "$0")/.."

echo "Building JDK base image..."
docker build -f Dockerfile -t parking-netty-framework:jdk-21 .

echo "Building application image..."
docker build -t parking-netty-framework:app .

echo "Build complete!"
docker images parking-netty-framework
```

---

## 11. 文件清单

| 文件 | 用途 |
|------|------|
| `Dockerfile` | Docker 镜像构建配置 |
| `docker-compose.yml` | Docker Compose 编排配置 |
| `nginx/nginx.conf` | Nginx 负载均衡配置 |
| `scripts/start.sh` | 启动脚本 |
| `scripts/stop.sh` | 停止脚本 |
| `scripts/build-docker.sh` | Docker 镜像构建脚本 |
| `scripts/docker-test.sh` | Docker 健康检查脚本 |

---

## 12. 最佳实践

1. **使用固定版本标签** - 避免使用 `latest` 标签
2. **多阶段构建** - 减小镜像大小
3. **健康检查** - 部署前验证容器状态
4. **日志持久化** - 使用 volume 挂载日志
5. **资源限制** - 设置 CPU 和内存限制
6. **重启策略** - 使用 `unless-stopped` 自动重启
7. **网络隔离** - 使用专用网络隔离服务
8. **配置分离** - 环境变量和配置文件分离
