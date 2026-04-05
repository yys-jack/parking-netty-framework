# 生产环境部署指南

**日期**: 2026-04-05  
**状态**: 已启用

---

## 1. 环境要求

| 组件 | 版本 | 说明 |
|------|------|------|
| JDK | 21+ | Alibaba Dragonwell 21.0.10 推荐 |
| MySQL | 8.0+ | 认证数据存储 |
| Redis | 6.0+ | 响应式缓存 (推荐) |
| Docker | 20+ | 可选，容器化部署 |

---

## 2. 数据库配置

### 2.1 创建数据库

```sql
CREATE DATABASE IF NOT EXISTS parking 
  DEFAULT CHARACTER SET utf8mb4 
  DEFAULT COLLATE utf8mb4_unicode_ci;

CREATE USER 'parking'@'%' IDENTIFIED BY 'your-password';
GRANT ALL PRIVILEGES ON parking.* TO 'parking'@'%';
FLUSH PRIVILEGES;
```

### 2.2 执行 Schema

```bash
# 连接到数据库
mysql -u parking -p parking

# 执行 schema
source /path/to/schema-mysql.sql
```

或直接执行:

```bash
mysql -u parking -p parking < src/main/resources/schema-mysql.sql
```

### 2.3 验证表结构

```sql
SHOW TABLES;
DESCRIBE auth_info;
```

---

## 3. Redis 配置

### 3.1 安装 Redis

```bash
# Ubuntu/Debian
sudo apt-get install redis-server

# CentOS/RHEL
sudo yum install redis
```

### 3.2 配置 Redis

编辑 `/etc/redis/redis.conf`:

```conf
# 绑定地址 (生产环境建议绑定内网 IP)
bind 127.0.0.1

# 端口
port 6379

# 密码 (生产环境必须设置)
requirepass your-strong-password

# 持久化 - RDB
save 900 1
save 300 10
save 60 10000

# 持久化 - AOF (推荐)
appendonly yes
appendfsync everysec

# 最大内存
maxmemory 256mb
maxmemory-policy allkeys-lru
```

### 3.3 启动 Redis

```bash
sudo systemctl start redis
sudo systemctl enable redis
```

### 3.4 验证 Redis

```bash
redis-cli ping
# 应输出：PONG

# 带密码验证
redis-cli -a your-password ping
```

---

## 4. 应用配置

### 4.1 application.yml 生产配置

```yaml
server:
  port: 8080

spring:
  config:
    import: auth.yml
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}
      password: ${REDIS_PASSWORD:}
      database: 0

netty:
  tcp:
    port: 8889
    auth-timeout: 10
    heartbeat-interval: 90
    backlog: 256

logging:
  level:
    cn.tnar.parking: INFO
    io.netty: WARN
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss} - %msg%n"
    file: "%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n"
  file:
    name: /var/log/parking-netty/app.log

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics
  endpoint:
    health:
      show-details: when-authorized
```

### 4.2 auth.yml 配置

```yaml
auth:
  storage:
    type: redis  # 生产环境使用 redis
    admin-token: ${ADMIN_TOKEN:change-me-in-production}
  redis:
    host: ${REDIS_HOST:localhost}
    port: ${REDIS_PORT:6379}
    password: ${REDIS_PASSWORD:}
    database: 0
    key-prefix: 'parking:auth:'
  jdbc:
    url: jdbc:mysql://${DB_HOST:localhost}:${DB_PORT:3306}/parking?useSSL=true&serverTimezone=Asia/Shanghai
    username: ${DB_USERNAME:parking}
    password: ${DB_PASSWORD:}
    table: auth_info
```

### 4.3 环境变量配置

```bash
# 导出环境变量
export REDIS_HOST=redis.internal
export REDIS_PORT=6379
export REDIS_PASSWORD=your-redis-password
export ADMIN_TOKEN=your-admin-token
export DB_HOST=mysql.internal
export DB_PORT=3306
export DB_USERNAME=parking
export DB_PASSWORD=your-db-password
```

---

## 5. 存储类型选择

### 5.1 存储类型对比

| 类型 | 适用场景 | 优点 | 缺点 |
|------|----------|------|------|
| in-memory | 开发/测试 | 零依赖，启动快 | 重启丢失，无法共享 |
| redis | 生产环境 (推荐) | 高性能，支持并发 | 需要 Redis 服务 |
| jdbc | 备用方案 | 持久化，易备份 | 性能较低 |

### 5.2 推荐配置

**开发环境**:
```yaml
auth:
  storage:
    type: in-memory
```

**测试环境**:
```yaml
auth:
  storage:
    type: redis  # 或 in-memory
```

**生产环境**:
```yaml
auth:
  storage:
    type: redis  # 必须使用 redis
```

---

## 6. 部署方式

### 6.1 Gradle 部署

```bash
# 构建
./gradlew bootJar

# 启动
java -jar build/libs/parking-netty-framework.jar \
  --spring.config.location=file:/etc/parking/application.yml,file:/etc/parking/auth.yml
```

### 6.2 Systemd 服务

创建 `/etc/systemd/system/parking-netty.service`:

```ini
[Unit]
Description=Parking Netty Framework
After=network.target redis.service mysql.service

[Service]
Type=simple
User=parking
WorkingDirectory=/opt/parking-netty
Environment=JAVA_HOME=/usr/lib/jvm/java-21
Environment=REDIS_HOST=redis.internal
Environment=REDIS_PASSWORD=your-password
ExecStart=/usr/lib/jvm/java-21/bin/java -jar parking-netty-framework.jar
Restart=on-failure

[Install]
WantedBy=multi-user.target
```

启动服务:

```bash
sudo systemctl daemon-reload
sudo systemctl start parking-netty
sudo systemctl enable parking-netty
```

### 6.3 Docker 部署

```bash
# 构建镜像
./gradlew bootJar
docker build -t parking-netty:latest .

# 启动容器
docker run -d \
  --name parking-netty \
  -p 8080:8080 \
  -p 8889:8889 \
  -e REDIS_HOST=redis \
  -e REDIS_PASSWORD=your-password \
  -e ADMIN_TOKEN=your-admin-token \
  --network your-network \
  parking-netty:latest
```

---

## 7. 监控和日志

### 7.1 Actuator 端点

```bash
# 健康检查
curl http://localhost:8080/actuator/health

# 应用信息
curl http://localhost:8080/actuator/info

# 指标 (需要添加 micrometer)
curl http://localhost:8080/actuator/metrics
```

### 7.2 日志配置

生产环境日志输出到文件:

```yaml
logging:
  file:
    name: /var/log/parking-netty/app.log
  logback:
    rollingpolicy:
      max-file-size: 10MB
      max-history: 30
```

### 7.3 监控指标

**关键指标**:
- TCP 连接数
- 认证成功/失败次数
- 消息处理延迟
- Redis 连接状态
- 数据库连接状态

---

## 8. 故障排查

### 8.1 Redis 连接失败

**症状**: `Cannot get Jedis connection` 或 `Connection refused`

**排查步骤**:

```bash
# 1. 检查 Redis 服务
sudo systemctl status redis

# 2. 检查网络连接
telnet redis-host 6379

# 3. 验证密码
redis-cli -h redis-host -a your-password ping

# 4. 检查应用日志
tail -f /var/log/parking-netty/app.log | grep -i redis
```

**解决方案**:
- 确认 Redis 服务运行正常
- 检查防火墙设置
- 验证密码配置

### 8.2 MySQL 连接失败

**症状**: `Communications link failure`

**排查步骤**:

```bash
# 1. 检查 MySQL 服务
sudo systemctl status mysql

# 2. 检查网络连接
telnet mysql-host 3306

# 3. 验证用户权限
mysql -u parking -p -h mysql-host -e "SHOW GRANTS;"

# 4. 检查应用日志
tail -f /var/log/parking-netty/app.log | grep -i mysql
```

### 8.3 认证失败

**症状**: 客户端返回 `CODE_MISMATCH` 或 `CODE_FAILURE`

**排查步骤**:

1. 检查数据库/Redis 中的认证数据:

```sql
SELECT * FROM auth_info WHERE serial = 'TEST-001';
```

```bash
redis-cli -a your-password HGETALL "parking:auth:TEST-001"
```

2. 确认客户端 serial/secret 匹配

3. 刷新 Redis 缓存 (Redis 模式):

```bash
curl -X POST \
  -H "X-Admin-Token: your-admin-token" \
  http://localhost:8080/api/auth/refresh
```

### 8.4 缓存刷新失败

**症状**: API 返回 401 或 500

**排查步骤**:

```bash
# 1. 验证 admin token
curl -X POST \
  -H "X-Admin-Token: your-admin-token" \
  -v http://localhost:8080/api/auth/refresh

# 2. 检查应用日志
tail -f /var/log/parking-netty/app.log | grep -i refresh
```

---

## 9. 性能优化

### 9.1 JVM 参数

```bash
java -Xms2g -Xmx4g -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -jar app.jar
```

### 9.2 Redis 优化

```conf
# 使用更高性能的持久化
appendfsync no

# 增加最大连接数
maxclients 10000
```

### 9.3 Netty 优化

```yaml
netty:
  tcp:
    backlog: 512  # 增加连接队列
    heartbeat-interval: 120  # 调整心跳间隔
```

---

## 10. 备份和恢复

### 10.1 数据库备份

```bash
# 备份
mysqldump -u parking -p parking > parking-backup-$(date +%Y%m%d).sql

# 恢复
mysql -u parking -p parking < parking-backup-20260405.sql
```

### 10.2 Redis 备份

```bash
# RDB 备份
cp /var/lib/redis/dump.rdb /backup/dump-$(date +%Y%m%d).rdb

# AOF 备份
cp /var/lib/redis/appendonly.aof /backup/appendonly-$(date +%Y%m%d).aof
```

---

## 11. 安全建议

1. **修改默认 admin-token**
2. **Redis 设置强密码**
3. **数据库限制访问 IP**
4. **使用 HTTPS (如果有 Web 界面)**
5. **定期更新依赖**
6. **启用日志审计**

---

## 12. 文件清单

| 文件 | 说明 |
|------|------|
| `src/main/resources/schema-mysql.sql` | MySQL 数据库结构 |
| `docs/DEVELOPMENT.md` | 开发指南 |
| `docs/TESTING.md` | 测试体系 |
| `docs/DOCKER_DEPLOYMENT.md` | Docker 部署指南 |
| `scripts/start.sh` | 启动脚本 |
| `scripts/stop.sh` | 停止脚本 |
