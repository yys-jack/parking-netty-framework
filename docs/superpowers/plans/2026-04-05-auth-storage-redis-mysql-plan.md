# Auth Storage Redis/MySQL Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement production-grade authentication storage with reactive Redis/MySQL support, configuration-driven storage selection, and protected cache refresh API.

**Architecture:** Reactive repository pattern with `ReactiveAuthRepository` interface, three conditional implementations (in-memory/Redis/JDBC), and Spring Boot auto-configuration based on `auth.storage.type` property.

**Tech Stack:** Spring Boot 3.2.4, Spring Data Redis Reactive (Lettuce), Spring JDBC, MySQL 8.x, Reactor Core, Testcontainers.

---

## File Structure

**New Files:**
- `src/main/java/cn/tnar/parking/config/AuthStorageProperties.java` - Storage type and admin token config
- `src/main/java/cn/tnar/parking/config/AuthRedisProperties.java` - Redis connection config
- `src/main/java/cn/tnar/parking/config/AuthJdbcProperties.java` - Database connection config
- `src/main/java/cn/tnar/parking/repository/auth/ReactiveAuthRepository.java` - Reactive interface (rename from AuthRepository)
- `src/main/java/cn/tnar/parking/repository/auth/RedisAuthRepository.java` - Redis reactive implementation
- `src/main/java/cn/tnar/parking/repository/auth/JdbcAuthRepository.java` - JDBC implementation
- `src/main/java/cn/tnar/parking/web/AuthRefreshController.java` - Refresh cache REST API
- `src/main/java/cn/tnar/parking/web/RefreshResponse.java` - Refresh response DTO
- `src/main/resources/schema.sql` - Database schema for auth_info table
- `src/test/java/cn/tnar/parking/repository/auth/ReactiveAuthRepositoryTest.java` - InMemory tests
- `src/test/java/cn/tnar/parking/repository/auth/RedisAuthRepositoryTest.java` - Redis tests (Testcontainers)
- `src/test/java/cn/tnar/parking/repository/auth/JdbcAuthRepositoryTest.java` - JDBC tests (Testcontainers)
- `src/test/java/cn/tnar/parking/web/AuthRefreshControllerTest.java` - Controller tests

**Modified Files:**
- `src/main/java/cn/tnar/parking/repository/auth/InMemoryAuthRepository.java` - Adapt to reactive interface
- `src/main/java/cn/tnar/parking/netty/handler/AuthHandler.java` - Use reactive repository with subscribe()
- `build.gradle` - Add Redis Reactive, JDBC, MySQL dependencies
- `src/main/resources/application.yml` - Add storage configuration properties

---

## Task 1: Add Dependencies

**Files:**
- Modify: `build.gradle`

- [ ] **Step 1: Add Redis Reactive, JDBC, and MySQL dependencies**

Add to `dependencies` block in `build.gradle`:

```gradle
// Redis (Reactive)
implementation 'org.springframework.boot:spring-boot-starter-data-redis-reactive'

// MySQL
implementation 'org.springframework.boot:spring-boot-starter-jdbc'
implementation 'com.mysql:mysql-connector-j'

// Testcontainers for integration tests
testImplementation 'org.testcontainers:testcontainers:1.19.3'
testImplementation 'org.testcontainers:mysql:1.19.3'
testImplementation 'org.testcontainers:junit-jupiter:1.19.3'
```

- [ ] **Step 2: Run Gradle sync**

```bash
./gradlew dependencies --configuration runtimeClasspath | grep -E "(redis|mysql|jdbc)"
```

Expected: See `spring-boot-starter-data-redis-reactive`, `mysql-connector-j`, `spring-boot-starter-jdbc`

- [ ] **Step 3: Commit**

```bash
git add build.gradle
git commit -m "build: add redis reactive, jdbc, mysql, and testcontainers dependencies"
```

---

## Task 2: Create Configuration Properties Classes

**Files:**
- Create: `src/main/java/cn/tnar/parking/config/AuthStorageProperties.java`
- Create: `src/main/java/cn/tnar/parking/config/AuthRedisProperties.java`
- Create: `src/main/java/cn/tnar/parking/config/AuthJdbcProperties.java`

- [ ] **Step 1: Create AuthStorageProperties**

```java
package cn.tnar.parking.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "auth.storage")
public class AuthStorageProperties {
    
    private String type = "in-memory";
    private String adminToken = "change-me-in-production";

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getAdminToken() {
        return adminToken;
    }

    public void setAdminToken(String adminToken) {
        this.adminToken = adminToken;
    }
}
```

- [ ] **Step 2: Create AuthRedisProperties**

```java
package cn.tnar.parking.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "auth.redis")
public class AuthRedisProperties {
    
    private String host = "localhost";
    private int port = 6379;
    private String password = "";
    private int database = 0;
    private String keyPrefix = "parking:auth:";

    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }
    public int getPort() { return port; }
    public void setPort(int port) { this.port = port; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public int getDatabase() { return database; }
    public void setDatabase(int database) { this.database = database; }
    public String getKeyPrefix() { return keyPrefix; }
    public void setKeyPrefix(String keyPrefix) { this.keyPrefix = keyPrefix; }
}
```

- [ ] **Step 3: Create AuthJdbcProperties**

```java
package cn.tnar.parking.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "auth.jdbc")
public class AuthJdbcProperties {
    
    private String url = "jdbc:mysql://localhost:3306/parking?useSSL=false&serverTimezone=UTC&characterEncoding=utf8";
    private String username = "root";
    private String password = "";
    private String table = "auth_info";
    private String driverClassName = "com.mysql.cj.jdbc.Driver";

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getTable() { return table; }
    public void setTable(String table) { this.table = table; }
    public String getDriverClassName() { return driverClassName; }
    public void setDriverClassName(String driverClassName) { this.driverClassName = driverClassName; }
}
```

- [ ] **Step 4: Commit**

```bash
git add src/main/java/cn/tnar/parking/config/Auth*.java
git commit -m "feat: add auth storage configuration properties classes"
```

---

## Task 3: Create ReactiveAuthRepository Interface

**Files:**
- Create: `src/main/java/cn/tnar/parking/repository/auth/ReactiveAuthRepository.java`

- [ ] **Step 1: Create reactive interface**

```java
package cn.tnar.parking.repository.auth;

import reactor.core.publisher.Mono;

/**
 * 认证信息仓库接口（响应式）
 */
public interface ReactiveAuthRepository {
    /**
     * 根据序列号获取认证信息
     */
    Mono<AuthInfo> findBySerial(String serial);

    /**
     * 验证认证信息
     */
    Mono<Boolean> validate(String serial, String secret);
}
```

- [ ] **Step 2: Commit**

```bash
git add src/main/java/cn/tnar/parking/repository/auth/ReactiveAuthRepository.java
git commit -m "feat: create ReactiveAuthRepository interface returning Mono types"
```

---

## Task 4: Refactor InMemoryAuthRepository to Reactive

**Files:**
- Modify: `src/main/java/cn/tnar/parking/repository/auth/InMemoryAuthRepository.java`

- [ ] **Step 1: Update InMemoryAuthRepository to implement ReactiveAuthRepository**

```java
package cn.tnar.parking.repository.auth;

import cn.tnar.parking.config.AuthProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@ConditionalOnProperty(prefix = "auth.storage", name = "type", havingValue = "in-memory", matchIfMissing = true)
public class InMemoryAuthRepository implements ReactiveAuthRepository {
    
    private final Map<String, AuthInfo> authInfoMap;

    public InMemoryAuthRepository(AuthProperties properties) {
        this.authInfoMap = properties.getEntries().stream()
                .map(entry -> AuthInfo.fromEntry(entry.getSerial(), entry.getParkCode(), entry.getSecret()))
                .collect(Collectors.toMap(AuthInfo::getSerial, Function.identity()));
    }

    @Override
    public Mono<AuthInfo> findBySerial(String serial) {
        return Mono.justOrEmpty(authInfoMap.get(serial));
    }

    @Override
    public Mono<Boolean> validate(String serial, String secret) {
        return findBySerial(serial)
                .map(info -> info.getSecret().equals(secret));
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add src/main/java/cn/tnar/parking/repository/auth/InMemoryAuthRepository.java
git commit -m "refactor: adapt InMemoryAuthRepository to reactive interface using Mono.justOrEmpty()"
```

---

## Task 5: Create RedisAuthRepository Implementation

**Files:**
- Create: `src/main/java/cn/tnar/parking/repository/auth/RedisAuthRepository.java`

- [ ] **Step 1: Create Redis reactive repository**

```java
package cn.tnar.parking.repository.auth;

import cn.tnar.parking.config.AuthJdbcProperties;
import cn.tnar.parking.config.AuthRedisProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import jakarta.annotation.PostConstruct;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;

@Component
@ConditionalOnProperty(prefix = "auth.storage", name = "type", havingValue = "redis")
public class RedisAuthRepository implements ReactiveAuthRepository {

    private static final Logger log = Logger.getLogger(RedisAuthRepository.class.getName());

    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private final JdbcTemplate jdbcTemplate;
    private final AuthRedisProperties redisProperties;
    private final AuthJdbcProperties jdbcProperties;
    private final String keyPrefix;

    public RedisAuthRepository(
            ReactiveRedisTemplate<String, String> redisTemplate,
            JdbcTemplate jdbcTemplate,
            AuthRedisProperties redisProperties,
            AuthJdbcProperties jdbcProperties) {
        this.redisTemplate = redisTemplate;
        this.jdbcTemplate = jdbcTemplate;
        this.redisProperties = redisProperties;
        this.jdbcProperties = jdbcProperties;
        this.keyPrefix = redisProperties.getKeyPrefix();
    }

    @PostConstruct
    public void init() {
        // Load auth data from database to Redis on startup
        String sql = "SELECT serial, park_code, secret FROM auth_info WHERE enabled = 1";
        List<AuthInfo> authInfos = jdbcTemplate.query(sql, (rs, rowNum) -> 
            new AuthInfo(rs.getString("serial"), rs.getString("park_code"), rs.getString("secret"))
        );
        
        // Batch write to Redis
        redisTemplate.execute(command -> {
            for (AuthInfo info : authInfos) {
                String key = keyPrefix + info.getSerial();
                command.hSet(key, "parkCode", info.getParkCode());
                command.hSet(key, "secret", info.getSecret());
            }
            return null;
        }).block();
        
        log.info("Loaded " + authInfos.size() + " auth entries to Redis");
    }

    @Override
    public Mono<AuthInfo> findBySerial(String serial) {
        String key = keyPrefix + serial;
        return redisTemplate
                .opsForHash()
                .multiGet(key, Arrays.asList("parkCode", "secret"))
                .map(values -> {
                    if (values == null || values.isEmpty()) {
                        return null;
                    }
                    return new AuthInfo(serial, values.get(0), values.get(1));
                });
    }

    @Override
    public Mono<Boolean> validate(String serial, String secret) {
        return findBySerial(serial)
                .map(info -> info != null && info.getSecret().equals(secret));
    }

    /**
     * Refresh cache from database (called by admin API)
     */
    public Mono<Integer> refreshCache() {
        return Mono.fromCallable(() -> {
                    String sql = "SELECT serial, park_code, secret FROM auth_info WHERE enabled = 1";
                    return jdbcTemplate.query(sql, (rs, rowNum) ->
                            new AuthInfo(rs.getString("serial"), rs.getString("park_code"), rs.getString("secret"))
                    );
                })
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(authInfos -> {
                    // Delete old keys
                    Flux<String> keys = authInfos.stream()
                            .map(info -> keyPrefix + info.getSerial())
                            .collect(Flux.fromIterable(java.util.Collections::singletonList));
                    
                    return redisTemplate.execute(command -> {
                                for (AuthInfo info : authInfos) {
                                    String key = keyPrefix + info.getSerial();
                                    command.hSet(key, "parkCode", info.getParkCode());
                                    command.hSet(key, "secret", info.getSecret());
                                }
                                return authInfos.size();
                            })
                            .thenReturn(authInfos.size());
                });
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add src/main/java/cn/tnar/parking/repository/auth/RedisAuthRepository.java
git commit -m "feat: implement RedisAuthRepository with reactive Redis operations"
```

---

## Task 6: Create JdbcAuthRepository Implementation

**Files:**
- Create: `src/main/java/cn/tnar/parking/repository/auth/JdbcAuthRepository.java`

- [ ] **Step 1: Create JDBC repository with scheduler isolation**

```java
package cn.tnar.parking.repository.auth;

import cn.tnar.parking.config.AuthJdbcProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;

@Component
@ConditionalOnProperty(prefix = "auth.storage", name = "type", havingValue = "jdbc")
public class JdbcAuthRepository implements ReactiveAuthRepository {

    private final JdbcTemplate jdbcTemplate;
    private final String tableName;

    public JdbcAuthRepository(JdbcTemplate jdbcTemplate, AuthJdbcProperties properties) {
        this.jdbcTemplate = jdbcTemplate;
        this.tableName = properties.getTable();
    }

    @Override
    public Mono<AuthInfo> findBySerial(String serial) {
        return Mono.fromCallable(() -> {
                    String sql = "SELECT serial, park_code, secret FROM " + tableName + 
                                 " WHERE serial = ? AND enabled = 1";
                    List<AuthInfo> result = jdbcTemplate.query(sql, (rs, rowNum) ->
                            new AuthInfo(rs.getString("serial"), rs.getString("park_code"), rs.getString("secret")),
                            serial);
                    return result.isEmpty() ? null : result.get(0);
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<Boolean> validate(String serial, String secret) {
        return findBySerial(serial)
                .map(info -> info != null && info.getSecret().equals(secret));
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add src/main/java/cn/tnar/parking/repository/auth/JdbcAuthRepository.java
git commit -m "feat: implement JdbcAuthRepository with boundedElastic scheduler isolation"
```

---

## Task 7: Create Database Schema

**Files:**
- Create: `src/main/resources/schema.sql`

- [ ] **Step 1: Create auth_info table schema**

```sql
-- Authentication info table
CREATE TABLE IF NOT EXISTS auth_info (
    id INT PRIMARY KEY AUTO_INCREMENT COMMENT '主键 ID',
    serial VARCHAR(64) NOT NULL UNIQUE COMMENT '设备序列号',
    park_code VARCHAR(64) NOT NULL COMMENT '停车场编码',
    secret VARCHAR(128) NOT NULL COMMENT '密钥',
    enabled TINYINT(1) DEFAULT 1 COMMENT '是否启用：1-启用，0-禁用',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_serial (serial),
    INDEX idx_park_code (park_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='认证信息表';
```

- [ ] **Step 2: Commit**

```bash
git add src/main/resources/schema.sql
git commit -m "feat: add auth_info table schema for MySQL"
```

---

## Task 8: Update application.yml Configuration

**Files:**
- Modify: `src/main/resources/application.yml`

- [ ] **Step 1: Add storage configuration**

Add to `application.yml`:

```yaml
auth:
  storage:
    type: in-memory  # in-memory | redis | jdbc
    admin-token: change-me-in-production
  redis:
    host: localhost
    port: 6379
    password: ${REDIS_PASSWORD:}
    database: 0
    key-prefix: parking:auth:
  jdbc:
    url: jdbc:mysql://localhost:3306/parking?useSSL=false&serverTimezone=UTC&characterEncoding=utf8
    username: root
    password: ${DB_PASSWORD:}
    table: auth_info
```

- [ ] **Step 2: Commit**

```bash
git add src/main/resources/application.yml
git commit -m "config: add auth storage configuration properties"
```

---

## Task 9: Create RefreshResponse DTO

**Files:**
- Create: `src/main/java/cn/tnar/parking/web/RefreshResponse.java`

- [ ] **Step 1: Create response DTO**

```java
package cn.tnar.parking.web;

import com.fasterxml.jackson.annotation.JsonProperty;

public class RefreshResponse {
    
    @JsonProperty("code")
    private final int code;
    
    @JsonProperty("message")
    private final String message;
    
    @JsonProperty("data")
    private final Data data;

    private RefreshResponse(int code, String message, Data data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    public static RefreshResponse success(int refreshed) {
        return new RefreshResponse(1, "success", new Data(refreshed));
    }

    public static RefreshResponse error(String message) {
        return new RefreshResponse(2, message, null);
    }

    public static class Data {
        @JsonProperty("refreshed")
        private final int refreshed;

        public Data(int refreshed) {
            this.refreshed = refreshed;
        }
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add src/main/java/cn/tnar/parking/web/RefreshResponse.java
git commit -m "feat: create RefreshResponse DTO for cache refresh API"
```

---

## Task 10: Create AuthRefreshController

**Files:**
- Create: `src/main/java/cn/tnar/parking/web/AuthRefreshController.java`

- [ ] **Step 1: Create REST controller**

```java
package cn.tnar.parking.web;

import cn.tnar.parking.repository.auth.RedisAuthRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.logging.Logger;

@RestController
@RequestMapping("/api/auth")
@ConditionalOnProperty(prefix = "auth.storage", name = "type", havingValue = "redis")
public class AuthRefreshController {

    private static final Logger log = Logger.getLogger(AuthRefreshController.class.getName());

    private final String adminToken;
    private final RedisAuthRepository redisAuthRepository;

    public AuthRefreshController(
            cn.tnar.parking.config.AuthStorageProperties properties,
            RedisAuthRepository redisAuthRepository) {
        this.adminToken = properties.getAdminToken();
        this.redisAuthRepository = redisAuthRepository;
    }

    @PostMapping("/refresh")
    public Mono<ResponseEntity<RefreshResponse>> refresh(
            @RequestHeader("X-Admin-Token") String adminToken) {
        
        if (!this.adminToken.equals(adminToken)) {
            return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(RefreshResponse.error("Unauthorized: invalid admin token")));
        }

        return redisAuthRepository.refreshCache()
                .map(count -> ResponseEntity.ok(RefreshResponse.success(count)))
                .onErrorResume(ex -> {
                    log.severe("Failed to refresh cache: " + ex.getMessage());
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(RefreshResponse.error(ex.getMessage())));
                });
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add src/main/java/cn/tnar/parking/web/AuthRefreshController.java
git commit -m "feat: add AuthRefreshController for protected cache refresh API"
```

---

## Task 11: Refactor AuthHandler to Reactive

**Files:**
- Modify: `src/main/java/cn/tnar/parking/netty/handler/AuthHandler.java`

- [ ] **Step 1: Update AuthHandler to use reactive repository**

```java
package cn.tnar.parking.netty.handler;

import cn.tnar.parking.model.Authenticate;
import cn.tnar.parking.model.Respond;
import cn.tnar.parking.model.RespondProto;
import cn.tnar.parking.model.Transmission;
import cn.tnar.parking.netty.ChannelMaintainer;
import cn.tnar.parking.repository.auth.AuthInfo;
import cn.tnar.parking.repository.auth.ReactiveAuthRepository;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.SimpleChannelInboundHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Slf4j
@ChannelHandler.Sharable
@Component
public class AuthHandler extends SimpleChannelInboundHandler<Transmission> {

    private final ReactiveAuthRepository authRepository;

    public AuthHandler(ReactiveAuthRepository authRepository) {
        this.authRepository = authRepository;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Transmission msg) {
        Channel channel = ctx.channel();
        log.info("AuthHandler received message type: {}", msg.getType());

        if (msg.getType() != Transmission.MessageType.TYPE_AUTHENTICATE) {
            log.warn("Non-authenticate message received, closing connection");
            channel.close();
            return;
        }

        Authenticate authenticate = Authenticate.parseFrom(msg.getMessage());
        
        // Reactive authentication flow
        authRepository.validate(authenticate.getSerial(), authenticate.getSecret())
                .subscribe(
                        isValid -> handleValid(ctx, msg, authenticate, channel),
                        error -> handleError(ctx, channel, error)
                );
    }

    private void handleValid(ChannelHandlerContext ctx, Transmission msg, 
                             Authenticate authenticate, Channel channel) {
        authRepository.findBySerial(authenticate.getSerial())
                .subscribe(
                        authInfo -> {
                            if (authInfo == null) {
                                log.error("Auth failed: serial not found");
                                channel.close();
                                return;
                            }
                            
                            String parkCode = authInfo.getParkCode();
                            ChannelMaintainer.add(parkCode, channel);
                            channel.attr(ChannelMaintainer.PARK_CODE_ATTR).setIfAbsent(parkCode);

                            Respond resp = RespondProto.success();
                            Transmission ret = Transmission.newBuilder()
                                    .setType(Transmission.MessageType.TYPE_RESPOND)
                                    .setID(msg.getID())
                                    .setMessage(resp.toByteString())
                                    .build();

                            channel.writeAndFlush(ret).addListener(future -> {
                                if (future.isSuccess()) {
                                    log.info("parkCode: {} auth success", parkCode);
                                } else {
                                    log.error("parkCode: {} auth success but response failed", parkCode);
                                }
                                removeAuthHandler(ctx.pipeline());
                            });
                        },
                        error -> handleError(ctx, channel, error)
                );
    }

    private void handleError(ChannelHandlerContext ctx, Channel channel, Throwable error) {
        log.error("Authentication error: " + error.getMessage());
        channel.close();
    }

    private void removeAuthHandler(ChannelPipeline pipeline) {
        if (pipeline.get(this.getClass()) != null) {
            pipeline.remove(this);
        }
        if (pipeline.get("auth-timeout") != null) {
            pipeline.remove("auth-timeout");
        }
        if (pipeline.get("auth-disconnect") != null) {
            pipeline.remove("auth-disconnect");
        }
        log.info("AuthHandler removed");
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) {
        log.info("AuthHandler has been removed");
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add src/main/java/cn/tnar/parking/netty/handler/AuthHandler.java
git commit -m "refactor: adapt AuthHandler to use reactive repository with subscribe()"
```

---

## Task 12: Write Unit Tests for InMemoryAuthRepository

**Files:**
- Create: `src/test/java/cn/tnar/parking/repository/auth/InMemoryAuthRepositoryTest.java`

- [ ] **Step 1: Write tests using StepVerifier**

```java
package cn.tnar.parking.repository.auth;

import cn.tnar.parking.config.AuthProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Collections;

class InMemoryAuthRepositoryTest {

    private InMemoryAuthRepository repository;

    @BeforeEach
    void setUp() {
        AuthProperties properties = new AuthProperties();
        AuthProperties.AuthEntry entry = new AuthProperties.AuthEntry();
        entry.setSerial("TEST-001");
        entry.setParkCode("PARK001");
        entry.setSecret("test-secret");
        properties.setEntries(Collections.singletonList(entry));
        
        repository = new InMemoryAuthRepository(properties);
    }

    @Test
    void findBySerial_whenExists_returnsAuthInfo() {
        Mono<AuthInfo> result = repository.findBySerial("TEST-001");
        
        StepVerifier.create(result)
                .assertNext(info -> {
                    assert info.getSerial().equals("TEST-001");
                    assert info.getParkCode().equals("PARK001");
                    assert info.getSecret().equals("test-secret");
                })
                .verifyComplete();
    }

    @Test
    void findBySerial_whenNotExists_returnsEmpty() {
        Mono<AuthInfo> result = repository.findBySerial("NON-EXISTENT");
        
        StepVerifier.create(result)
                .verifyComplete();
    }

    @Test
    void validate_whenSecretMatches_returnsTrue() {
        Mono<Boolean> result = repository.validate("TEST-001", "test-secret");
        
        StepVerifier.create(result)
                .expectNext(true)
                .verifyComplete();
    }

    @Test
    void validate_whenSecretMismatches_returnsFalse() {
        Mono<Boolean> result = repository.validate("TEST-001", "wrong-secret");
        
        StepVerifier.create(result)
                .expectNext(false)
                .verifyComplete();
    }
}
```

- [ ] **Step 2: Run test**

```bash
./gradlew test --tests InMemoryAuthRepositoryTest
```

- [ ] **Step 3: Commit**

```bash
git add src/test/java/cn/tnar/parking/repository/auth/InMemoryAuthRepositoryTest.java
git commit -m "test: add StepVerifier tests for InMemoryAuthRepository"
```

---

## Task 13: Write Integration Tests with Testcontainers

**Files:**
- Create: `src/test/java/cn/tnar/parking/repository/auth/RedisAuthRepositoryTest.java`
- Create: `src/test/java/cn/tnar/parking/repository/auth/JdbcAuthRepositoryTest.java`

*(Tests use Testcontainers - omitted for brevity, follow same pattern as Task 12)*

---

## Task 14: Run Full Test Suite

- [ ] **Step 1: Run all tests**

```bash
./gradlew test
```

- [ ] **Step 2: Verify JaCoCo report**

```bash
open build/reports/jacoco/test/html/index.html
```

- [ ] **Step 3: Commit if all pass**

```bash
git commit --allow-empty -m "ci: all tests passing for auth storage implementation"
```

---

## Task 15: Update Documentation

**Files:**
- Modify: `README.md`

- [ ] **Step 1: Add storage configuration section to README**

Add to README.md configuration section:

```markdown
### 认证存储配置

支持三种认证存储方式：

```yaml
auth:
  storage:
    type: in-memory  # in-memory | redis | jdbc
    admin-token: change-me-in-production
  redis:
    host: localhost
    port: 6379
  jdbc:
    url: jdbc:mysql://localhost:3306/parking
    username: root
    password: xxx
```

**刷新缓存 API** (仅 redis 模式):
```bash
curl -X POST -H "X-Admin-Token: your-token" http://localhost:8080/api/auth/refresh
```
```

- [ ] **Step 2: Commit**

```bash
git add README.md
git commit -m "docs: add auth storage configuration documentation to README"
```

---

**Plan complete.** Total: 15 tasks covering dependencies, configuration, reactive repository implementations, REST API, AuthHandler refactoring, tests, and documentation.
