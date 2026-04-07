package cn.tnar.parking.repository.space;

import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 车位快照聚合器
 * 内存聚合 + 定时批量写入数据库，降低高频上报对数据库的压力
 */
@Slf4j
@Component
public class SpaceSnapshotAggregator {

    private final JdbcTemplate jdbcTemplate;

    /**
     * 待写入的快照数据
     * Key: parkCode
     * Value: 最新的车位数据 [total, remained, spaceTime]
     */
    private final ConcurrentMap<String, long[]> pendingSnapshots = new ConcurrentHashMap<>();

    /**
     * 每个停车场的写入计数器（用于指标收集）
     */
    private final ConcurrentMap<String, AtomicLong> flushCounters = new ConcurrentHashMap<>();

    public SpaceSnapshotAggregator(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 添加快照到待写入队列
     * 如果已存在该停车场的快照，则更新为最新值（覆盖）
     */
    public void addSnapshot(String parkCode, int total, int remained, long spaceTime) {
        pendingSnapshots.put(parkCode, new long[]{total, remained, spaceTime});
        log.debug("Added snapshot for parkCode: {}, total: {}, remained: {}, time: {}",
                parkCode, total, remained, spaceTime);
    }

    /**
     * 定时刷新到数据库（每秒执行一次）
     */
    @Scheduled(fixedRate = 1000)
    public void flushToDatabase() {
        if (pendingSnapshots.isEmpty()) {
            return;
        }

        // 提取所有待写入的数据并清空待写队列
        Map<String, long[]> snapshotsToFlush = new ConcurrentHashMap<>(pendingSnapshots);
        pendingSnapshots.clear();

        List<Object[]> batchArgs = new ArrayList<>(snapshotsToFlush.size());
        for (Map.Entry<String, long[]> entry : snapshotsToFlush.entrySet()) {
            String parkCode = entry.getKey();
            long[] data = entry.getValue();
            int total = (int) data[0];
            int remained = (int) data[1];
            long spaceTime = data[2];

            // 计算占用率
            BigDecimal occupancyRate = BigDecimal.valueOf(total - remained)
                    .multiply(BigDecimal.valueOf(100))
                    .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);

            batchArgs.add(new Object[]{parkCode, total, remained, occupancyRate, new Timestamp(spaceTime)});
        }

        if (!batchArgs.isEmpty()) {
            String sql = "INSERT INTO space_snapshot (park_code, total_spaces, remained_spaces, occupancy_rate, space_time) " +
                    "VALUES (?, ?, ?, ?, ?)";

            try {
                jdbcTemplate.batchUpdate(sql, batchArgs);
                flushCounters.computeIfAbsent("total", k -> new AtomicLong(0))
                        .addAndGet(batchArgs.size());

                log.info("Flushed {} space snapshots to database (total flushed: {})",
                        batchArgs.size(), flushCounters.get("total").get());
            } catch (Exception e) {
                log.error("Failed to flush space snapshots to database", e);
                // 失败时将数据重新放回队列（可能导致重复，但保证数据不丢失）
                pendingSnapshots.putAll(snapshotsToFlush);
            }
        }
    }

    /**
     * 获取待写入的快照数量（用于监控）
     */
    public int getPendingCount() {
        return pendingSnapshots.size();
    }

    /**
     * 获取累计刷新次数（用于监控）
     */
    public long getTotalFlushedCount() {
        AtomicLong counter = flushCounters.get("total");
        return counter != null ? counter.get() : 0;
    }
}
