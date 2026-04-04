# Parking Netty Framework

停车场 Netty 通信框架 - 简化的云云对接框架

## 项目结构

```
parking-netty-framework/
├── src/main/java/cn/tnar/parking/
│   ├── NettyApplication.java          # Spring Boot 应用入口
│   ├── config/                        # 配置类
│   │   ├── AuthProperties.java        # 认证配置属性
│   │   ├── JacksonProtobufSupport.java
│   │   └── NettyProperties.java       # Netty 配置属性
│   ├── framework/                     # 框架核心
│   │   └── AbstractHandler.java       # 抽象处理器基类
│   ├── netty/                         # Netty 核心
│   │   ├── ApiServer.java             # Netty 服务器
│   │   ├── ChannelMaintainer.java     # Channel 管理器
│   │   ├── OperationResultFuture.java # 异步操作 Future
│   │   └── handler/
│   │       ├── AuthHandler.java       # 认证处理器
│   │       ├── DispatcherHandler.java # 消息分派器
│   │       ├── DemoHandler.java       # 演示 Handler
│   │       ├── SpaceHandler.java      # 车位上报 Handler
│   │       ├── CarInHandler.java      # 车辆入场 Handler
│   │       ├── CarOutHandler.java     # 车辆出场 Handler
│   │       └── Handler.java           # Handler 接口
│   ├── repository/                    # 数据访问层
│   │   └── auth/
│   │       ├── AuthRepository.java    # 认证仓库接口
│   │       ├── AuthInfo.java          # 认证信息实体
│   │       └── InMemoryAuthRepository.java  # 内存实现
│   ├── task/
│   │   └── HandlerRegistry.java       # Handler 注册表
│   └── model/                         # 模型类
│       ├── RespondProto.java          # 响应工厂 (手写)
│       ├── BusinessException.java     # 业务异常
│       └── ExistedFutureException.java
├── src/main/proto/
│   └── message.proto                  # Protobuf 定义
├── src/main/resources/
│   ├── application.yml                # 应用配置
│   └── auth.yml                       # 认证配置
├── src/test/java/cn/tnar/parking/
│   ├── client/                        # 测试客户端
│   ├── integration/                   # 集成测试
│   ├── netty/handler/                 # Handler 单元测试
│   ├── model/
│   ├── netty/
│   └── task/
├── build.gradle
├── settings.gradle
├── test-client.sh                      # 测试客户端脚本
└── scripts/
    ├── start.sh                        # 启动服务器脚本
    ├── stop.sh                         # 停止服务器脚本
    ├── scale-up.sh                     # 扩容脚本
    └── scale-down.sh                   # 缩容脚本
```

## 核心特性

1. **Netty TCP 服务器** - 基于 Netty 4.1 的高性能 TCP 服务器
2. **Protobuf 序列化** - 使用 Google Protobuf 进行高效序列化
3. **认证机制** - 客户端连接需要先认证 (serial/secret)
4. **心跳检测** - 支持心跳检测和空闲超时断开
5. **消息分派** - 基于消息类型的自动分派处理
6. **Repository 模式** - 认证存储使用 Repository 模式，支持多种实现
7. **配置驱动** - 认证信息通过 auth.yml 配置，支持热更新
8. **AbstractHandler** - 统一的处理器基类，提供日志和异常处理
9. **Spring Boot 集成** - 支持 Spring Boot 自动配置和依赖注入

## 快速开始

### 1. 启动服务器

```bash
# 方式一：使用 Gradle
./gradlew bootRun

# 方式二：使用启动脚本
./scripts/start.sh
```

服务器默认端口：
- HTTP 端口：8080 (Spring Boot Actuator)
- TCP 端口：8889 (Netty)

### 2. 运行测试客户端

```bash
# 方式一：使用 Gradle
./gradlew runClient \
    -Pclient.host=localhost \
    -Pclient.port=8889 \
    -Pclient.serial=TEST-001 \
    -Pclient.secret=test-secret

# 方式二：使用客户端脚本
./test-client.sh localhost 8889 TEST-001 test-secret
```

### 3. 验证连接

客户端成功连接后，会依次发送：
1. 认证消息 (TYPE_AUTHENTICATE)
2. Demo 消息 (TYPE_DEMO)
3. 车位上报消息 (TYPE_SPACE)
4. 车辆入场消息 (TYPE_CAR_IN)

服务端日志示例：
```
AuthHandler received message type: TYPE_AUTHENTICATE
parkCode : PARK001  认证成功，响应已发送

接收消息，parkCode : PARK001, msgType : TYPE_DEMO
DemoHandler 处理消息，parkCode: PARK001, ID: 1001

SpaceHandler 接收车位上报，parkCode: PARK001, 总车位：100, 剩余车位：50

CarInHandler 接收车辆入场，parkCode: PARK001, 车牌：陕 A12345, 通道：ENTRANCE-01
```

## 添加自定义 Handler

1. 实现 `Handler` 接口:

```java
@Component
public class MyHandler implements Handler {
    @Override
    public Respond handle(Transmission transmission, String parkCode) {
        // 处理逻辑
        log.info("处理消息，parkCode: {}", parkCode);
        return RespondProto.success();
    }

    @Override
    public Transmission.MessageType processedType() {
        return Transmission.MessageType.TYPE_DEMO;
    }
}
```

2. Spring 会自动通过 `HandlerRegistry` 注册到消息分派器

## Protobuf 编译

```bash
# 编译 proto 文件生成 Java 类
./gradlew generateProto

# 完整构建
./gradlew build
```

生成的 Java 类位于：`build/generated/source/proto/main/java/cn/tnar/parking/model/`

## 配置说明

### application.yml

```yaml
server:
  port: 8080

netty:
  tcp:
    port: 8889           # TCP 监听端口
    auth-timeout: 10     # 认证超时时间 (秒)
    heartbeat-interval: 90  # 心跳间隔 (秒)
    backlog: 128         # 连接队列大小

logging:
  level:
    cn.tnar.parking: DEBUG
    io.netty: INFO

management:
  endpoints:
    web:
      exposure:
        include: health,info
```

### 认证配置

认证信息通过 `auth.yml` 配置：

```yaml
auth:
  entries:
    - serial: TEST-001
      parkCode: PARK001
      secret: test-secret
    - serial: PROD-002
      parkCode: PARK002
      secret: prod-secret-xyz
```

生产环境可使用 Redis 或数据库实现 `AuthRepository` 接口。

**Spring Boot 3.x 迁移说明**：
- `@PostConstruct` / `@PreDestroy`: 从 `javax.annotation` 迁移到 `jakarta.annotation`

### 配置项说明

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| server.port | 8080 | HTTP 端口 (Actuator) |
| netty.tcp.port | 8889 | TCP 端口 |
| netty.tcp.auth-timeout | 10 | 认证超时时间 (秒) |
| netty.tcp.heartbeat-interval | 90 | 心跳间隔 (秒) |
| netty.tcp.backlog | 128 | 连接队列大小 |
| auth.entries | - | 认证列表 (serial/parkCode/secret) |

**注意**: Spring Boot 3.x 使用 Jakarta EE 9+，包名从 `javax.*` 迁移到 `jakarta.*`。

## 消息类型

| 类型 | ID | 说明 |
|------|-----|------|
| TYPE_RESPOND | 1 | 通用响应 |
| TYPE_AUTHENTICATE | 2 | 认证消息 |
| TYPE_SPACE | 3 | 车位上报 |
| TYPE_CAR_IN | 5 | 车辆入场 |
| TYPE_CAR_OUT | 6 | 车辆出场 |
| TYPE_DEMO | 100 | 演示消息 |

## 响应码

| 响应码 | 值 | 说明 |
|--------|-----|------|
| CODE_SUCCESS | 1 | 成功 |
| CODE_FAILURE | 2 | 失败 |
| CODE_DUPLICATE | 3 | 重复 |
| CODE_MISMATCH | 4 | 不匹配 |
| CODE_NOT_SUPPORTED | 5 | 暂不支持 |
| CODE_OFFLINE | 6 | 离线 |
| CODE_TIMEOUT | 7 | 超时 |

## 通信协议

### 帧格式

```
+----------------+------------------+
| 长度 (4 字节)     | Protobuf 数据     |
| Little-Endian  |                  |
+----------------+------------------+
```

### 认证流程

1. 客户端建立 TCP 连接
2. 服务端启动认证倒计时 (10 秒)
3. 客户端发送 `TYPE_AUTHENTICATE` 消息 (包含 serial 和 secret)
4. 服务端验证通过，返回 `TYPE_RESPOND` (CODE_SUCCESS)
5. 服务端移除 AuthHandler，客户端移除 ClientAuthHandler
6. 后续消息直接由 DispatcherHandler 分派处理

### 消息结构

```protobuf
message Transmission {
  fixed32     ID      = 1; // 消息 ID
  MessageType type    = 2; // 消息类型
  bytes       message = 3; // 具体消息序列化后的字节
}
```

## 单元测试

```bash
# 运行所有测试
./gradlew test

# 查看测试报告
open build/reports/tests/test/index.html
```

测试覆盖：
- `RespondProtoTest` - 响应工厂测试
- `HandlerRegistryTest` - Handler 注册表测试
- `ChannelMaintainerTest` - Channel 管理测试
- `OperationResultFutureTest` - 异步 Future 测试
- `SpaceHandlerTest`, `CarInHandlerTest`, `CarOutHandlerTest`, `DemoHandlerTest` - Handler 单元测试
- `AuthIntegrationTest` - 认证集成测试

## 依赖

- Spring Boot 3.2.4
- Spring Framework 6.x
- JDK 21
- Netty 4.1.108.Final
- Protobuf 3.25.3
- Lombok (注解工具)
- JUnit 5 (测试)

## 故障排查

### 客户端连接失败

1. 检查服务端是否启动：`netstat -tlnp | grep 8889`
2. 检查防火墙设置
3. 确认认证信息 (serial/secret) 正确

### 认证失败

1. 检查 `auth.yml` 中是否配置了对应的 serial
2. 确认 secret 匹配
3. 查看服务端日志中的认证错误信息

### 消息未处理

1. 检查 Handler 是否正确注册到 Spring 容器
2. 确认 `processedType()` 返回的消息类型正确
3. 查看 DispatcherHandler 日志

## 扩展建议

### 生产环境配置

1. **认证存储**：替换为 Redis 或数据库
2. **日志配置**：调整日志级别和输出格式
3. **监控告警**：集成 Prometheus + Grafana

### 添加新消息类型

1. 在 `message.proto` 中添加消息定义
2. 运行 `./gradlew generateProto` 生成 Java 类
3. 在 `Transmission.MessageType` 枚举中添加新类型
4. 创建对应的 Handler 实现
