package cn.tnar.parking.config;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SchedulerMetricsTest {

    @Test
    void testSchedulerMetricsAreRegistered() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        SchedulerConfig config = new SchedulerConfig();
        SchedulerMetrics metrics = new SchedulerMetrics(registry, config);

        // 触发指标注册
        metrics.registerMetrics();

        // 验证数据库调度器指标已注册
        assertNotNull(registry.find("scheduler_pool_size").tag("scheduler", "database").gauge());
        assertNotNull(registry.find("scheduler_queue_size").tag("scheduler", "database").gauge());
        assertNotNull(registry.find("scheduler_active_threads").tag("scheduler", "database").gauge());

        // 验证外部调度器指标已注册
        assertNotNull(registry.find("scheduler_pool_size").tag("scheduler", "external").gauge());
        assertNotNull(registry.find("scheduler_queue_size").tag("scheduler", "external").gauge());
        assertNotNull(registry.find("scheduler_active_threads").tag("scheduler", "external").gauge());
    }
}
