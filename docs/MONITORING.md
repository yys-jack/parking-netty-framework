# 监控指南

**日期**: 2026-04-05
**状态**: 已启用

---

## 1. 架构概述

本项目使用 Micrometer + Prometheus + Grafana 构建监控体系。

```
┌─────────────────────────────────────────┐
│         Parking Netty Application       │
│  ┌─────────┐ ┌──────────┐ ┌──────────┐ │
│  │ApiServer│ │AuthHandler│ │Dispatcher│ │
│  │(连接数)  │ │(认证指标)  │ │(业务指标) │ │
│  └────┬────┘ └─────┬────┘ └────┬─────┘ │
│       └────────────┴───────────┘        │
│                 │                       │
│          ┌──────▼──────┐                │
│          │ MetricsAspect│               │
│          └──────┬──────┘                │
└─────────────────┼───────────────────────┘
                  │ /actuator/prometheus
                  ▼
┌─────────────────────────────────────────┐
│            Prometheus                    │
│  抓取间隔：15s                           │
│  告警规则：6 条                           │
└─────────────────┼───────────────────────┘
                  │
                  ▼
┌─────────────────────────────────────────┐
│             Grafana                      │
│  仪表板：Parking Netty Dashboard         │
│  面板：7 个（连接/认证/消息/告警）          │
└─────────────────────────────────────────┘
```

---

## 2. 指标说明

### 2.1 TCP 连接指标

| 指标名称 | 类型 | 说明 |
|---------|------|------|
| `parking_netty_connections_active` | Gauge | 当前活跃连接数 |
| `parking_netty_connections_total` | Counter | 累计连接数 |
| `parking_netty_disconnections_total` | Counter | 累计断开数 |

### 2.2 认证指标

| 指标名称 | 类型 | 说明 | 标签 |
|---------|------|------|------|
| `parking_netty_auth_success_total` | Counter | 认证成功次数 | `park_code` |
| `parking_netty_auth_failure_total` | Counter | 认证失败次数 | `park_code`, `reason` |
| `parking_netty_auth_duration_seconds` | Histogram | 认证耗时 | `park_code` |

### 2.3 业务指标

| 指标名称 | 类型 | 说明 | 标签 |
|---------|------|------|------|
| `parking_netty_messages_total` | Counter | 消息处理总数 | `message_type`, `park_code` |
| `parking_netty_messages_failure_total` | Counter | 消息处理失败数 | `message_type`, `park_code` |
| `parking_netty_message_duration_seconds` | Histogram | 消息处理耗时 | `message_type`, `park_code` |

---

## 3. Prometheus 配置

### 3.1 配置文件

位置：`prometheus/prometheus.yml`

```yaml
global:
  scrape_interval: 15s
  evaluation_interval: 15s

rule_files:
  - "alert_rules.yml"

scrape_configs:
  - job_name: 'parking-netty'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['host.docker.internal:8081']
```

### 3.2 启动 Prometheus

```bash
docker compose -f docker-compose.monitoring.yml up -d prometheus
```

访问：http://localhost:9090

---

## 4. Grafana 仪表板

### 4.1 访问

访问：http://localhost:3000
- 用户名：admin
- 密码：admin

### 4.2 仪表板面板

1. **TCP Connection Count** - 实时连接数 + 趋势图
2. **Auth Success vs Failure** - 认证成功/失败对比
3. **Auth Latency Distribution** - 认证延迟热力图
4. **Message Processing Rate** - 消息处理速率
5. **Message Latency P99** - 消息处理延迟
6. **Alert Status** - 告警状态

---

## 5. 告警规则

### 5.1 配置文件

位置：`prometheus/alert_rules.yml`

### 5.2 告警列表

| 告警 | 条件 | 严重度 |
|------|------|--------|
| HighConnectionCount | 连接数 > 1000 (5m) | warning |
| CriticalConnectionCount | 连接数 > 5000 (5m) | critical |
| HighAuthFailureRate | 失败率 > 10% (5m) | warning |
| HighMessageLatency | P99 > 500ms (5m) | warning |
| CriticalMessageLatency | P99 > 1s (5m) | critical |
| TargetDown | 应用下线 (1m) | critical |

---

## 6. 故障排查

### 6.1 Prometheus 无法抓取指标

**症状**: Targets 显示 DOWN

**排查**:
```bash
# 检查应用是否启动
curl http://localhost:8081/actuator/prometheus

# 检查 Prometheus 配置
docker compose -f docker-compose.monitoring.yml exec prometheus cat /etc/prometheus/prometheus.yml
```

### 6.2 Grafana 无法连接数据源

**症状**: Dashboard 显示"No data"

**排查**:
```bash
# 检查 Prometheus 是否运行
docker compose -f docker-compose.monitoring.yml ps

# 检查数据源配置
docker compose -f docker-compose.monitoring.yml exec grafana cat /etc/grafana/provisioning/datasources/datasource.yml
```
