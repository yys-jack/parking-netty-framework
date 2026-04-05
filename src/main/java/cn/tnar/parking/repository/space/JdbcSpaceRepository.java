package cn.tnar.parking.repository.space;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
        validateSaveParams(parkCode, total, remained);

        BigDecimal occupancyRate = BigDecimal.valueOf(total - remained)
            .multiply(BigDecimal.valueOf(100))
            .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);

        jdbcTemplate.update(INSERT_SQL,
            parkCode,
            total,
            remained,
            occupancyRate,
            new Timestamp(spaceTime));

        log.debug("保存车位快照，parkCode: {}, 总车位：{}, 剩余：{}, 占用率：{}%",
            parkCode, total, remained, occupancyRate);
    }

    /**
     * 验证保存参数
     */
    private void validateSaveParams(String parkCode, int total, int remained) {
        if (parkCode == null || parkCode.isBlank()) {
            throw new IllegalArgumentException("停车场编码不能为空");
        }
        if (total <= 0) {
            throw new IllegalArgumentException("总车位数必须大于 0");
        }
        if (remained < 0) {
            throw new IllegalArgumentException("剩余车位数不能小于 0");
        }
        if (remained > total) {
            throw new IllegalArgumentException("剩余车位数不能大于总车位数");
        }
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
