# Netty Docker 分布式部署指南

## 快速开始

### 启动

```bash
./scripts/start.sh
```

### 查看状态

```bash
docker compose ps
docker compose logs -f
```

### 停止

```bash
./scripts/stop.sh
```

## 环境信息

| 组件 | 版本 |
|------|------|
| JDK | 21 (Alibaba Dragonwell 21.0.10) |
| Spring Boot | 3.2.4 |
| Netty | 4.1.108.Final |
| Docker Compose | 2.x |

## Docker 镜像构建

### 自定义 JDK 镜像

本项目使用本地 JDK 21 (Alibaba Dragonwell) 构建自定义 Docker 基础镜像，确保与本地开发环境一致。

### 构建命令

```bash
# 构建 JDK 基础镜像和应用镜像
./scripts/build-docker.sh

# 或分别构建
docker build -f Dockerfile.jdk -t parking-netty-framework:jdk-21 .
docker build -t parking-netty-framework:app .
```

### 镜像信息

| 镜像 | 说明 |
|------|------|
| `parking-netty-framework:jdk-21` | 自定义 JDK 21 基础镜像（基于 Debian bookworm-slim + Dragonwell） |
| `parking-netty-framework:app` | 应用镜像（基于自定义 JDK 镜像） |
| `parking-netty-framework-netty-server-*` | Docker Compose 构建的实例镜像 |

## 架构

- Nginx 监听 8889 端口作为 TCP 负载均衡器
- 后端 2 个 Netty 实例分别监听 8890 和 8891
- 支持动态增加节点

## 零中断扩容

当前架构支持零中断动态扩容：
- 预置 10 个上游槽位（2 个活动 + 8 个备用）
- 启动备用节点时无需重启 Nginx
- 长连接保持不中断

### 扩容命令

```bash
# 启动第 3 个节点（端口 8892）
./scripts/scale-up.sh 3
# 或
docker compose --profile scale up -d netty-server-3

# 启动所有备用节点
docker compose --profile scale up -d
```

### 缩容命令

```bash
# 停止第 3 个节点
./scripts/scale-down.sh 3
# 或
docker compose stop netty-server-3
```

## 动态增加节点

1. 修改 `docker-compose.yml`，添加新服务：

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

2. 修改 `nginx/nginx.conf`，添加 upstream：

```nginx
server netty-server-3:8892 max_fails=3 fail_timeout=30s;
```

3. 重启新服务和 Nginx：

```bash
docker compose up -d netty-server-3
docker exec nginx-lb nginx -s reload
```

## 故障处理

### 查看日志

```bash
# 所有服务
docker compose logs -f

# 单个服务
docker logs netty-server-1
docker logs nginx-lb
```

### 重启单个服务

```bash
docker compose restart netty-server-1
```

### 重建镜像

```bash
docker compose build --no-cache
```
