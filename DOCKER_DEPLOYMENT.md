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
