# 监控报警系统设计文档

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 实现基于 Prometheus + Grafana 的监控报警系统，覆盖 TCP 连接、认证、业务处理全链路指标

**Architecture:** 使用 Micrometer 收集指标，暴露 Prometheus 端点，Grafana 展示仪表板，Prometheus Alertmanager 发送告警

**Tech Stack:** Spring Boot Actuator, Micrometer, Prometheus, Grafana, Docker Compose

---

## 1. 架构设计

### 1.1 整体架构

```
┌─────────────────────────────────────────────────────────────┐
│                   Parking Netty Application                 │
│  ┌─────────────┐  ┌──────────────┐  ┌─────────────────┐    │
│  │   ApiServer │  │ AuthHandler  │  │ DispatcherHandler│    │
│  │  (连接数)    │  │ (认证指标)    │  │   (业务指标)     │    │
│  └──────┬──────┘  └──────┬───────┘  └────────┬────────┘    │
│         │                │                    │             │
│         └────────────────┴────────────────────┘             │
│                           │                                 │
│                  ┌────────▼────────┐                        │
│                  │  Micrometer     │                        │
│                  │  Registry       │                        │
│                  └────────┬────────┘                        │
└───────────────────────────┼─────────────────────────────────┘
                            │ /actuator/prometheus
                            ▼
┌─────────────────────────────────────────────────────────────┐
│                      Prometheus                              │
│  ┌──────────────────────────────────────────────────────┐   │
│  │  抓取配置：每 15 秒                                     │   │
│  │  告警规则：连接数、认证失败率、延迟                    │   │
│  └──────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────┐
│                        Grafana                               │
│  ┌─────────────────┐  ┌─────────────────────────────────┐   │
│  │ Spring Boot     │  │ Parking Netty 自定义仪表板       │   │
│  │ Dashboard 11378 │  │ - TCP 连接数                       │   │
│  │ (JVM 指标)       │  │ - 认证成功/失败                   │   │
│  │                 │  │ - 消息处理延迟                    │   │
│  │                 │  │ - 告警状态面板                    │   │
│  └─────────────────┘  └─────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
```

### 1.2 组件职责

| 组件 | 职责 |
|------|------|
| **Micrometer** | 指标收集框架，统一不同监控系统的 API |
| **Prometheus** | 时序数据库，抓取、存储、告警 |
| **Grafana** | 数据可视化，仪表板展示 |
| **Alertmanager** | 告警路由、去重、静默 |

---

## 2. 指标设计

### 2.1 TCP 连接层指标

| 指标名称 | 类型 | 说明 | 标签 |
|---------|------|------|------|
| `parking_netty_connections_active` | Gauge | 当前活跃连接数 | - |
| `parking_netty_connections_total` | Counter | 累计连接数 | - |
| `parking_netty_disconnections_total` | Counter | 累计断开数 | - |

### 2.2 认证层指标

| 指标名称 | 类型 | 说明 | 标签 |
|---------|------|------|------|
| `parking_netty_auth_success_total` | Counter | 认证成功次数 | `park_code` |
| `parking_netty_auth_failure_total` | Counter | 认证失败次数 | `park_code`, `reason` |
| `parking_netty_auth_duration_seconds` | Histogram | 认证耗时 | `park_code` |

Histogram buckets: 0.001, 0.005, 0.01, 0.025, 0.05, 0.1, 0.25, 0.5, 1.0

### 2.3 业务层指标

| 指标名称 | 类型 | 说明 | 标签 |
|---------|------|------|------|
| `parking_netty_messages_total` | Counter | 消息处理总数 | `message_type`, `park_code` |
| `parking_netty_messages_failure_total` | Counter | 消息处理失败数 | `message_type`, `park_code`, `error_type` |
| `parking_netty_message_duration_seconds` | Histogram | 消息处理耗时 | `message_type`, `park_code` |

Histogram buckets: 0.001, 0.005, 0.01, 0.025, 0.05, 0.1, 0.25, 0.5, 1.0, 2.5, 5.0

### 2.4 指标收集点

| 位置 | 收集的指标 |
|------|-----------|
| `ApiServer.java` | 连接建立/断开事件 |
| `AuthHandler` | 认证成功/失败/耗时 |
| `DispatcherHandler` | 消息处理总数/耗时/失败 |
| `AbstractHandler` | 业务异常统计 |

---

## 3. 告警规则

### 3.1 连接告警

```yaml
groups:
  - name: parking-netty
    interval: 30s
    rules:
      # 高连接数警告
      - alert: HighConnectionCount
        expr: parking_netty_connections_active > 1000
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "高连接数警告 - {{ $value }} 个连接"
          description: "过去 5 分钟活跃连接数超过 1000"

      # 高连接数严重
      - alert: CriticalConnectionCount
        expr: parking_netty_connections_active > 5000
        for: 5m
        labels:
          severity: critical
        annotations:
          summary: "高连接数严重告警 - {{ $value }} 个连接"
          description: "过去 5 分钟活跃连接数超过 5000"
```

### 3.2 认证告警

```yaml
      # 认证失败率高
      - alert: HighAuthFailureRate
        expr: |
          (
            sum(rate(parking_netty_auth_failure_total[5m]))
            /
            sum(rate(parking_netty_auth_success_total[5m]) + rate(parking_netty_auth_failure_total[5m]))
          ) > 0.1
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "认证失败率偏高 - {{ $value | humanizePercentage }}"
          description: "过去 5 分钟认证失败率超过 10%"
```

### 3.3 性能告警

```yaml
      # 消息处理延迟高
      - alert: HighMessageLatency
        expr: |
          histogram_quantile(0.99, 
            sum(rate(parking_netty_message_duration_seconds_bucket[5m])) by (le)
          ) > 0.5
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "消息处理 P99 延迟高 - {{ $value }}s"
          description: "过去 5 分钟消息处理 P99 延迟超过 500ms"

      # 消息处理延迟严重
      - alert: CriticalMessageLatency
        expr: |
          histogram_quantile(0.99, 
            sum(rate(parking_netty_message_duration_seconds_bucket[5m])) by (le)
          ) > 1.0
        for: 5m
        labels:
          severity: critical
        annotations:
          summary: "消息处理 P99 延迟严重 - {{ $value }}s"
          description: "过去 5 分钟消息处理 P99 延迟超过 1 秒"
```

### 3.4 系统告警

```yaml
      # 应用下线
      - alert: TargetDown
        expr: up{job="parking-netty"} == 0
        for: 1m
        labels:
          severity: critical
        annotations:
          summary: "应用 {{ $labels.instance }} 已下线"
          description: "Prometheus 无法抓取到应用指标"
```

---

## 4. 部署配置

### 4.1 文件结构

```
parking-netty-framework/
├── build.gradle                          # 添加 micrometer-registry-prometheus
├── src/main/java/cn/tnar/parking/
│   ├── config/
│   │   └── MetricsConfig.java            # 自定义指标配置
│   └── aspect/
│       └── MetricsAspect.java            # AOP 切面收集指标
├── prometheus/
│   └── prometheus.yml                    # Prometheus 配置
└── grafana/
    ├── provisioning/
    │   ├── datasources/
    │   │   └── datasource.yml
    │   └── dashboards/
    │       └── dashboards.yml
    └── dashboards/
        └── parking-netty-dashboard.json
```

### 4.2 Docker Compose 配置

**集成模式** (docker-compose.yml):
```yaml
services:
  prometheus:
    image: prom/prometheus:v2.45.0
    ports:
      - "9090:9090"
    volumes:
      - ./prometheus/prometheus.yml:/etc/prometheus/prometheus.yml
      - prometheus_data:/prometheus
    depends_on:
      - app

  grafana:
    image: grafana/grafana:10.0.0
    ports:
      - "3000:3000"
    environment:
      - GF_SECURITY_ADMIN_PASSWORD=admin
    volumes:
      - ./grafana/provisioning:/etc/grafana/provisioning
      - ./grafana/dashboards:/var/lib/grafana/dashboards
      - grafana_data:/var/lib/grafana
    depends_on:
      - prometheus

  app:
    # ... existing app config
    ports:
      - "8080:8080"
      - "8889:8889"
      - "8081:8081"  # 管理端口
```

**独立模式** (docker-compose.monitoring.yml):
```yaml
version: '3.8'
services:
  prometheus:
    # ... same as above
    extra_hosts:
      - "host.docker.internal:host-gateway"

  grafana:
    # ... same as above

volumes:
  prometheus_data:
  grafana_data:
```

### 4.3 Prometheus 配置

```yaml
global:
  scrape_interval: 15s
  evaluation_interval: 15s

alerting:
  alertmanagers:
    - static_configs:
        - targets: []

rule_files:
  - "alert_rules.yml"

scrape_configs:
  - job_name: 'parking-netty'
    static_configs:
      - targets: ['host.docker.internal:8081']
        # 本地测试用
        # - targets: ['localhost:8081']
    metrics_path: '/actuator/prometheus'
```

---

## 5. Grafana 仪表板

### 5.1 数据源自动配置

`grafana/provisioning/datasources/datasource.yml`:
```yaml
apiVersion: 1
datasources:
  - name: Prometheus
    type: prometheus
    access: proxy
    url: http://prometheus:9090
    isDefault: true
    editable: true
```

### 5.2 仪表板自动配置

`grafana/provisioning/dashboards/dashboards.yml`:
```yaml
apiVersion: 1
providers:
  - name: 'Parking Netty'
    orgId: 1
    folder: ''
    type: file
    disableDeletion: false
    updateIntervalSeconds: 30
    options:
      path: /var/lib/grafana/dashboards
```

### 5.3 自定义仪表板内容

自定义仪表板包含以下面板：

1. **TCP 连接概览**
   - 活跃连接数（实时）
   - 连接数趋势（24 小时）

2. **认证统计**
   - 认证成功/失败对比
   - 认证失败率趋势
   - 认证延迟分布

3. **消息处理**
   - 消息处理速率（msg/s）
   - 消息类型分布
   - 消息处理延迟（P50/P90/P99）

4. **告警状态**
   - 当前告警列表
   - 告警历史

---

## 6. 测试策略

### 6.1 单元测试

- `MetricsConfigTest` - 验证指标配置
- `MetricsAspectTest` - 验证 AOP 切面拦截

### 6.2 集成测试

- 启动 Docker Compose
- 验证 Prometheus 可以抓取指标
- 验证 Grafana 仪表板加载

### 6.3 告警测试

- 模拟高连接数场景
- 模拟认证失败场景
- 验证告警触发

---

## 7. 验收标准

- [ ] 添加 `micrometer-registry-prometheus` 依赖
- [ ] 创建 `MetricsConfig.java` 配置类
- [ ] 创建 `MetricsAspect.java` AOP 切面
- [ ] 在 `ApiServer` 中收集连接数指标
- [ ] 在 `AuthHandler` 中收集认证指标
- [ ] 在 `DispatcherHandler` 中收集业务指标
- [ ] 创建 `prometheus/prometheus.yml` 配置
- [ ] 创建 `prometheus/alert_rules.yml` 告警规则
- [ ] 创建 `grafana/provisioning/datasources/datasource.yml`
- [ ] 创建 `grafana/provisioning/dashboards/dashboards.yml`
- [ ] 创建 `grafana/dashboards/parking-netty-dashboard.json`
- [ ] 创建 `docker-compose.monitoring.yml` 独立部署配置
- [ ] 更新 `docker-compose.yml` 集成监控服务
- [ ] 创建 `MetricsConfigTest` 和 `MetricsAspectTest`
- [ ] 所有测试通过
- [ ] 本地启动 Docker Compose 验证仪表板

---

## 8. 后续扩展

- 集成 Alertmanager 发送邮件/钉钉告警
- 添加分布式追踪（Sleuth + Zipkin）
- 添加日志聚合（ELK）
