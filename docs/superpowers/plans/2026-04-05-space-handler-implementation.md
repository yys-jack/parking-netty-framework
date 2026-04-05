# SpaceHandler 数据入库功能实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为 SpaceHandler 添加车位状态快照入库功能，实现停车场实时车位数据的持久化存储

**Architecture:** 基于 JDBC 的 Repository 模式，使用项目已有的 databaseScheduler 线程池隔离数据库操作，支持 H2/MySQL 双数据库通过 profile 切换

**Tech Stack:** Spring Boot 3.2.4, JDBC Template, Reactor Scheduler, H2/MySQL, Lombok, JUnit5

---

## File Structure

| 文件 | 类型 | 职责 |
|------|------|------|
| `src/main/resources/schema.sql` | 修改 | H2 数据库表结构定义 |
| `src/main/resources/schema-mysql.sql` | 修改 | MySQL 数据库表结构定义 |
| `src/main/java/cn/tnar/parking/repository/space/SpaceSnapshot.java` | 创建 | 车位快照实体类 |
| `src/main/java/cn/tnar/parking/repository/space/SpaceRepository.java` | 创建 | Repository 接口 |
| `src/main/java/cn/tnar/parking/repository/space/JdbcSpaceRepository.java` | 创建 | JDBC 实现类 |
| `src/main/java/cn/tnar/parking/netty/handler/SpaceHandler.java` | 修改 | 添加数据入库逻辑 |
| `src/test/java/cn/tnar/parking/repository/space/JdbcSpaceRepositoryTest.java` | 创建 | Repository 单元测试 |
| `src/test/java/cn/tnar/parking/netty/handler/SpaceHandlerTest.java` | 修改 | Handler 集成测试 |

---

## Task 1: 创建数据库表结构

**Files:**
- Modify: `src/main/resources/schema.sql:1-14`
- Modify: `src/main/resources/schema-mysql.sql` (参照 schema.sql 结构)

- [ ] **Step 1: 修改 schema.sql 添加 space_snapshot 表**

在 `src/main/resources/schema.sql` 的 `auth_info` 表定义之后添加：

```sql
-- Space snapshot table for parking space status tracking
CREATE TABLE IF NOT EXISTS space_snapshot (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    park_code VARCHAR(32) NOT NULL,
    total_spaces INT NOT NULL,
    remained_spaces INT NOT NULL,
    occupancy_rate DECIMAL(5,2),
    space_time TIMESTAMP NOT NULL,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_park_code ON space_snapshot(park_code);
CREATE INDEX IF NOT EXISTS idx_space_time ON space_snapshot(space_time);
```

- [ ] **Step 2: 修改 schema-mysql.sql 添加相同的表结构**

在 `src/main/resources/schema-mysql.sql` 末尾添加：

```sql
-- Space snapshot table for parking space status tracking
CREATE TABLE IF NOT EXISTS space_snapshot (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    park_code VARCHAR(32) NOT NULL,
    total_spaces INT NOT NULL,
    remained_spaces INT NOT NULL,
    occupancy_rate DECIMAL(5,2),
    space_time TIMESTAMP NOT NULL,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_park_code ON space_snapshot(park_code);
CREATE INDEX idx_space_time ON space_snapshot(space_time);
```

- [ ] **Step 3: 验证 SQL 语法**

```bash
./gradlew bootRun
```
预期：应用启动成功，数据库表自动创建

---

## Task 2: 创建 SpaceSnapshot 实体类

**Files:**
- Create: `src/main/java/cn/tnar/parking/repository/space/SpaceSnapshot.java`

- [ ] **Step 1: 创建实体类**

```java
package cn.tnar.parking.repository.space;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 车位状态快照实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SpaceSnapshot {
    private Long id;
    private String parkCode;
    private Integer totalSpaces;
    private Integer remainedSpaces;
    private BigDecimal occupancyRate;
    private Instant spaceTime;
    private Instant createTime;
}
```

- [ ] **Step 2: 验证编译**

```bash
./gradlew compileJava
```
预期：编译成功，无错误

---

## Task 3: 创建 SpaceRepository 接口

**Files:**
- Create: `src/main/java/cn/tnar/parking/repository/space/SpaceRepository.java`

- [ ] **Step 1: 创建 Repository 接口**

```java
package cn.tnar.parking.repository.space;

import java.util.List;
import java.util.Optional;

/**
 * 车位快照数据访问接口
 */
public interface SpaceRepository {
    /**
     * 保存车位快照
     * @param parkCode 停车场编码
     * @param total 总车位数
     * @param remained 剩余车位数
     * @param spaceTime 设备上报时间戳 (毫秒)
     */
    void saveSpaceSnapshot(String parkCode, int total, int remained, long spaceTime);

    /**
     * 查询停车场最新快照
     * @param parkCode 停车场编码
     * @return 最新快照，不存在则返回空
     */
    Optional<SpaceSnapshot> findLatestByParkCode(String parkCode);

    /**
     * 按时间范围查询快照
     * @param parkCode 停车场编码
     * @param startTime 开始时间戳 (毫秒)
     * @param endTime 结束时间戳 (毫秒)
     * @return 快照列表，按时间升序
     */
    List<SpaceSnapshot> findByParkCodeAndTimeRange(String parkCode, long startTime, long endTime);
}
```

- [ ] **Step 2: 验证编译**

```bash
./gradlew compileJava
```
预期：编译成功，无错误

---

## Task 4: 创建 JdbcSpaceRepository 实现类

**Files:**
- Create: `src/main/java/cn/tnar/parking/repository/space/JdbcSpaceRepository.java`

- [ ] **Step 1: 创建 JDBC 实现类**

```java
package cn.tnar.parking.repository.space;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 基于 JDBC 的车位快照数据访问实现
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class JdbcSpaceRepository implements SpaceRepository {

    private final JdbcTemplate jdbcTemplate;

    private static final String INSERT_SQL =
        "INSERT INTO space_snapshot (park_code, total_spaces, remained_spaces, occupancy_rate, space_time) " +
        "VALUES (?, ?, ?, ?, ?)";

    private static final String FIND_LATEST_SQL =
        "SELECT * FROM space_snapshot WHERE park_code = ? ORDER BY space_time DESC LIMIT 1";

    private static final String FIND_BY_TIME_RANGE_SQL =
        "SELECT * FROM space_snapshot WHERE park_code = ? AND space_time BETWEEN ? AND ? ORDER BY space_time ASC";

    @Override
    public void saveSpaceSnapshot(String parkCode, int total, int remained, long spaceTime) {
        BigDecimal occupancyRate = BigDecimal.valueOf((total - remained) * 100.0 / total)
            .setScale(2, BigDecimal.ROUND_HALF_UP);

        jdbcTemplate.update(INSERT_SQL,
            parkCode,
            total,
            remained,
            occupancyRate,
            new Timestamp(spaceTime));

        log.debug("保存车位快照，parkCode: {}, 总车位：{}, 剩余：{}, 占用率：{}%",
            parkCode, total, remained, occupancyRate);
    }

    @Override
    public Optional<SpaceSnapshot> findLatestByParkCode(String parkCode) {
        try {
            SpaceSnapshot snapshot = jdbcTemplate.queryForObject(
                FIND_LATEST_SQL,
                snapshotRowMapper(),
                parkCode
            );
            return Optional.ofNullable(snapshot);
        } catch (Exception e) {
            log.debug("未找到停车场 {} 的快照数据", parkCode);
            return Optional.empty();
        }
    }

    @Override
    public List<SpaceSnapshot> findByParkCodeAndTimeRange(String parkCode, long startTime, long endTime) {
        return jdbcTemplate.query(
            FIND_BY_TIME_RANGE_SQL,
            snapshotRowMapper(),
            parkCode,
            new Timestamp(startTime),
            new Timestamp(endTime)
        );
    }

    private RowMapper<SpaceSnapshot> snapshotRowMapper() {
        return (rs, rowNum) -> mapResultSetToSnapshot(rs);
    }

    private SpaceSnapshot mapResultSetToSnapshot(ResultSet rs) throws SQLException {
        return SpaceSnapshot.builder()
            .id(rs.getLong("id"))
            .parkCode(rs.getString("park_code"))
            .totalSpaces(rs.getInt("total_spaces"))
            .remainedSpaces(rs.getInt("remained_spaces"))
            .occupancyRate(rs.getBigDecimal("occupancy_rate"))
            .spaceTime(rs.getTimestamp("space_time").toInstant())
            .createTime(rs.getTimestamp("create_time").toInstant())
            .build();
    }
}
```

- [ ] **Step 2: 验证编译**

```bash
./gradlew compileJava
```
预期：编译成功，无错误

---

## Task 5: 改造 SpaceHandler 添加数据入库逻辑

**Files:**
- Modify: `src/main/java/cn/tnar/parking/netty/handler/SpaceHandler.java:1-35`

- [ ] **Step 1: 修改 SpaceHandler 添加 Repository 依赖和数据入库逻辑**

```java
package cn.tnar.parking.netty.handler;

import cn.tnar.parking.aspect.MetricsAspect;
import cn.tnar.parking.framework.AbstractHandler;
import cn.tnar.parking.model.Respond;
import cn.tnar.parking.model.RespondProto;
import cn.tnar.parking.model.Space;
import cn.tnar.parking.model.Transmission;
import cn.tnar.parking.repository.space.SpaceRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 车位上报处理器
 */
@Slf4j
@Component
public class SpaceHandler extends AbstractHandler {

    private final SpaceRepository spaceRepository;

    public SpaceHandler(MetricsAspect metricsAspect, SpaceRepository spaceRepository) {
        super(metricsAspect);
        this.spaceRepository = spaceRepository;
    }

    @Override
    public Transmission.MessageType processedType() {
        return Transmission.MessageType.TYPE_SPACE;
    }

    @Override
    protected Respond doHandle(Transmission transmission, String parkCode) throws Exception {
        Space space = Space.parseFrom(transmission.getMessage());

        // 保存车位快照到数据库
        spaceRepository.saveSpaceSnapshot(
            parkCode,
            space.getTotal(),
            space.getRemained(),
            System.currentTimeMillis()
        );

        log.info("SpaceHandler 接收车位上报，parkCode: {}, 总车位：{}, 剩余车位：{}",
                parkCode, space.getTotal(), space.getRemained());

        return RespondProto.success();
    }
}
```

- [ ] **Step 2: 验证编译**

```bash
./gradlew compileJava
```
预期：编译成功

---

## Task 6: 编写 JdbcSpaceRepository 单元测试

**Files:**
- Create: `src/test/java/cn/tnar/parking/repository/space/JdbcSpaceRepositoryTest.java`

- [ ] **Step 1: 创建 Repository 测试类**

```java
package cn.tnar.parking.repository.space;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.context.ActiveProfiles.PropertiesMode.OVERRIDE;

/**
 * JdbcSpaceRepository 集成测试
 */
@SpringBootTest
@AutoConfigureTestDatabase
@ActiveProfiles(value = "test", propertiesMode = OVERRIDE)
class JdbcSpaceRepositoryTest {

    @Autowired
    private JdbcSpaceRepository spaceRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        // 清空测试数据
        jdbcTemplate.update("DELETE FROM space_snapshot");
    }

    @Test
    void saveSpaceSnapshot_savesDataCorrectly() {
        // Given
        String parkCode = "PARK001";
        int total = 100;
        int remained = 60;
        long spaceTime = System.currentTimeMillis();

        // When
        spaceRepository.saveSpaceSnapshot(parkCode, total, remained, spaceTime);

        // Then
        Optional<SpaceSnapshot> saved = spaceRepository.findLatestByParkCode(parkCode);
        assertTrue(saved.isPresent());
        SpaceSnapshot snapshot = saved.get();
        assertEquals(parkCode, snapshot.getParkCode());
        assertEquals(total, snapshot.getTotalSpaces());
        assertEquals(remained, snapshot.getRemainedSpaces());
        assertEquals(BigDecimal.valueOf(40.00), snapshot.getOccupancyRate());
    }

    @Test
    void saveSpaceSnapshot_calculatesOccupancyRateCorrectly() {
        // Given
        String parkCode = "PARK002";
        int total = 200;
        int remained = 50;
        long spaceTime = System.currentTimeMillis();

        // When
        spaceRepository.saveSpaceSnapshot(parkCode, total, remained, spaceTime);

        // Then
        Optional<SpaceSnapshot> saved = spaceRepository.findLatestByParkCode(parkCode);
        assertTrue(saved.isPresent());
        // (200-50)/200 * 100 = 75.00
        assertEquals(BigDecimal.valueOf(75.00), saved.get().getOccupancyRate());
    }

    @Test
    void findLatestByParkCode_returnsEmptyWhenNotFound() {
        // When
        Optional<SpaceSnapshot> result = spaceRepository.findLatestByParkCode("NON_EXISTENT");

        // Then
        assertTrue(result.isEmpty());
    }

    @Test
    void findLatestByParkCode_returnsLatestSnapshot() {
        // Given
        String parkCode = "PARK003";
        long now = System.currentTimeMillis();

        spaceRepository.saveSpaceSnapshot(parkCode, 100, 80, now - 10000);
        spaceRepository.saveSpaceSnapshot(parkCode, 100, 50, now);

        // When
        Optional<SpaceSnapshot> result = spaceRepository.findLatestByParkCode(parkCode);

        // Then
        assertTrue(result.isPresent());
        assertEquals(50, result.get().getRemainedSpaces());
    }

    @Test
    void findByParkCodeAndTimeRange_returnsFilteredResults() {
        // Given
        String parkCode = "PARK004";
        long startTime = System.currentTimeMillis();

        spaceRepository.saveSpaceSnapshot(parkCode, 100, 90, startTime);
        spaceRepository.saveSpaceSnapshot(parkCode, 100, 70, startTime + 5000);
        spaceRepository.saveSpaceSnapshot(parkCode, 100, 50, startTime + 10000);

        // When - 查询前 5 秒的数据
        List<SpaceSnapshot> results = spaceRepository.findByParkCodeAndTimeRange(
            parkCode,
            startTime,
            startTime + 6000
        );

        // Then
        assertEquals(2, results.size());
        assertEquals(90, results.get(0).getRemainedSpaces());
        assertEquals(70, results.get(1).getRemainedSpaces());
    }

    @Test
    void findByParkCodeAndTimeRange_returnsEmptyWhenNoDataInRange() {
        // Given
        String parkCode = "PARK005";
        long now = System.currentTimeMillis();

        spaceRepository.saveSpaceSnapshot(parkCode, 100, 80, now - 100000);

        // When - 查询未来时间范围
        List<SpaceSnapshot> results = spaceRepository.findByParkCodeAndTimeRange(
            parkCode,
            now,
            now + 10000
        );

        // Then
        assertTrue(results.isEmpty());
    }
}
```

- [ ] **Step 2: 运行测试**

```bash
./gradlew test --tests "JdbcSpaceRepositoryTest"
```
预期：所有测试通过

---

## Task 7: 更新 SpaceHandler 单元测试

**Files:**
- Modify: `src/test/java/cn/tnar/parking/netty/handler/SpaceHandlerTest.java:1-57`

- [ ] **Step 1: 修改 SpaceHandlerTest 添加 Mock Repository**

```java
package cn.tnar.parking.netty.handler;

import cn.tnar.parking.aspect.MetricsAspect;
import cn.tnar.parking.model.Respond;
import cn.tnar.parking.model.RespondProto;
import cn.tnar.parking.model.Space;
import cn.tnar.parking.model.Transmission;
import cn.tnar.parking.repository.space.SpaceRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * SpaceHandler 单元测试
 */
class SpaceHandlerTest {

    private SpaceHandler handler;
    private MetricsAspect metricsAspect;
    private SpaceRepository spaceRepository;

    @BeforeEach
    void setUp() {
        metricsAspect = new MetricsAspect(new SimpleMeterRegistry());
        spaceRepository = mock(SpaceRepository.class);
        handler = new SpaceHandler(metricsAspect, spaceRepository);
    }

    @Test
    void testHandle_Success() {
        // Given
        Space space = Space.newBuilder()
                .setTotal(100)
                .setRemained(50)
                .build();
        Transmission transmission = Transmission.newBuilder()
                .setID(1001)
                .setType(Transmission.MessageType.TYPE_SPACE)
                .setMessage(space.toByteString())
                .build();
        String parkCode = "PARK001";

        // When & Then
        reactor.core.publisher.Mono<Respond> result = handler.handle(transmission, parkCode);

        StepVerifier.create(result)
            .assertNext(respond -> {
                assertEquals(Respond.RespondCode.CODE_SUCCESS, respond.getCode());
            })
            .verifyComplete();

        // Verify repository was called
        verify(spaceRepository, times(1))
            .saveSpaceSnapshot(anyString(), anyInt(), anyInt(), anyLong());
    }

    @Test
    void testHandle_savesCorrectData() {
        // Given
        int total = 100;
        int remained = 60;
        Space space = Space.newBuilder()
                .setTotal(total)
                .setRemained(remained)
                .build();
        Transmission transmission = Transmission.newBuilder()
                .setID(1002)
                .setType(Transmission.MessageType.TYPE_SPACE)
                .setMessage(space.toByteString())
                .build();
        String parkCode = "PARK002";

        // When
        handler.handle(transmission, parkCode).block();

        // Then
        verify(spaceRepository, times(1))
            .saveSpaceSnapshot(parkCode, total, remained, anyLong());
    }

    @Test
    void testProcessedType() {
        // When & Then
        assertEquals(Transmission.MessageType.TYPE_SPACE, handler.processedType());
    }
}
```

- [ ] **Step 2: 运行测试**

```bash
./gradlew test --tests "SpaceHandlerTest"
```
预期：所有测试通过

---

## Task 8: 运行完整测试套件并验证

**Files:**
- All test files

- [ ] **Step 1: 运行完整测试套件**

```bash
./gradlew test
```
预期：所有测试通过

- [ ] **Step 2: 运行代码风格检查**

```bash
./gradlew checkstyleMain checkstyleTest
```
预期：无代码风格违规

- [ ] **Step 3: 验证代码覆盖率报告**

```bash
./gradlew jacocoTestReport
```
预期：SpaceHandler 和 JdbcSpaceRepository 覆盖率 > 80%

---

## Verification

**完成标准:**
- 所有单元测试通过
- 代码风格检查通过
- SpaceHandler 成功集成 SpaceRepository
- 数据库表结构在 H2 和 MySQL 中都能正确创建

**可选手动验证:**

```bash
./gradlew bootRun
```

然后通过 H2 Console (`http://localhost:8080/h2-console`) 查询数据：
```sql
SELECT * FROM space_snapshot ORDER BY create_time DESC LIMIT 10;
```
