# SpaceHandler 数据入库设计文档

**日期**: 2026-04-05  
**状态**: 待实施

---

## 1. 背景与目标

### 1.1 现状
当前 `SpaceHandler` 仅记录日志，不持久化车位上报数据：
- 无法查询停车场历史车位状态
- 无法进行车位占用率分析
- 无法支持运营报表需求

### 1.2 目标
为 SpaceHandler 添加数据入库功能，实现停车场车位状态快照的持久化存储。

---

## 2. 设计决策

| 决策点 | 选择 | 理由 |
|--------|------|------|
| 数据模式 | 车位状态快照 | 记录停车场实时状态，支持快速查询最新状态 |
| 数据库支持 | H2 + MySQL (profile 切换) | 与项目现有数据库架构一致 |
| 时间戳设计 | 双时间戳 (space_time + create_time) | space_time 用于业务分析，create_time 用于数据稽核 |
| 占用率计算 | 存储计算值 | 避免每次查询都计算，利于快速检索 |
| Repository 风格 | 阻塞式 JDBC | 与当前 Handler 风格一致，改造幅度小 |
| 线程池 | databaseScheduler | 复用项目已有专用数据库线程池，可独立监控 |

---

## 3. 数据库设计

### 3.1 表结构

```sql
CREATE TABLE space_snapshot (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    park_code       VARCHAR(32) NOT NULL,       -- 停车场编码
    total_spaces    INT NOT NULL,               -- 总车位数
    remained_spaces INT NOT NULL,               -- 剩余车位数
    occupancy_rate  DECIMAL(5,2),               -- 占用率 = (total-remained)/total*100
    space_time      TIMESTAMP NOT NULL,         -- 设备上报时间
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,  -- 实际入库时间
    INDEX idx_park_code (park_code),
    INDEX idx_space_time (space_time)
);
```

### 3.2 索引说明

| 索引名 | 字段 | 用途 |
|--------|------|------|
| PRIMARY KEY | id | 主键 |
| idx_park_code | park_code | 按停车场查询 |
| idx_space_time | space_time | 按时间范围查询 |

### 3.3 DDL 脚本位置

- `src/main/resources/db/migration/V1__create_space_snapshot_table.sql` (MySQL)
- `src/main/resources/db/h2/schema.sql` (H2)

---

## 4. 代码设计

### 4.1 实体类

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

### 4.2 Repository 接口

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
     */
    void saveSpaceSnapshot(String parkCode, int total, int remained, long spaceTime);
    
    /**
     * 查询停车场最新快照
     */
    Optional<SpaceSnapshot> findLatestByParkCode(String parkCode);
    
    /**
     * 按时间范围查询快照
     */
    List<SpaceSnapshot> findByParkCodeAndTimeRange(String parkCode, long startTime, long endTime);
}
```

### 4.3 Repository 实现

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
            new Timestamp(spaceTime * 1000));  // proto fixed64 is milliseconds
        
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
            new Timestamp(startTime * 1000),
            new Timestamp(endTime * 1000)
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

### 4.4 Handler 改造

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
            System.currentTimeMillis()  // 使用当前时间作为上报时间
        );
        
        log.info("SpaceHandler 接收车位上报，parkCode: {}, 总车位：{}, 剩余车位：{}",
                parkCode, space.getTotal(), space.getRemained());
        
        return RespondProto.success();
    }
}
```

---

## 5. 配置说明

### 5.1 数据库配置

**H2 (开发/测试)** - `application-h2.yml`:
```yaml
spring:
  datasource:
    url: jdbc:h2:mem:parking;DB_CLOSE_DELAY=-1
    driver-class-name: org.h2.Driver
    username: sa
    password:
  h2:
    console:
      enabled: true
```

**MySQL (生产)** - `application-mysql.yml`:
```yaml
spring:
  datasource:
    url: jdbc:mysql://${DB_HOST:localhost}:${DB_PORT:3306}/${DB_NAME:parking}?useSSL=false&serverTimezone=UTC
    driver-class-name: com.mysql.cj.jdbc.Driver
    username: ${DB_USERNAME:root}
    password: ${DB_PASSWORD:}
```

### 5.2 线程池配置

```yaml
# application.yml
scheduler:
  database:
    core-threads: 5
    max-threads: 20
    queue-capacity: 100
```

---

## 6. 测试计划

### 6.1 单元测试

- `JdbcSpaceRepositoryTest` - 测试 Repository 层
- `SpaceHandlerTest` - 测试 Handler 集成

### 6.2 测试场景

| 场景 | 预期结果 |
|------|----------|
| 正常上报车位数据 | 成功入库，占用率计算正确 |
| 查询最新快照 | 返回最新一条记录 |
| 按时间范围查询 | 返回时间范围内的所有记录 |
| 占用率精度验证 | 保留 2 位小数，四舍五入 |

---

## 7. 实施清单

- [ ] 创建数据库表结构 (H2 + MySQL)
- [ ] 实现 SpaceSnapshot 实体类
- [ ] 实现 SpaceRepository 接口
- [ ] 实现 JdbcSpaceRepository
- [ ] 改造 SpaceHandler
- [ ] 编写单元测试
- [ ] 更新开发文档
