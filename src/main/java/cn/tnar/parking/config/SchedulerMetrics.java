package cn.tnar.parking.config;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;
import reactor.core.scheduler.Scheduler;

import jakarta.annotation.PostConstruct;
import java.lang.reflect.Field;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 调度器指标类
 * 监控线程池大小、队列大小、活跃线程数
 */
@Component
public class SchedulerMetrics {

    private final MeterRegistry meterRegistry;
    private final SchedulerConfig schedulerConfig;

    public SchedulerMetrics(MeterRegistry meterRegistry, SchedulerConfig schedulerConfig) {
        this.meterRegistry = meterRegistry;
        this.schedulerConfig = schedulerConfig;
    }

    @PostConstruct
    public void registerMetrics() {
        registerSchedulerMetrics(
            "database",
            schedulerConfig.databaseScheduler(schedulerConfig.databaseSchedulerProperties())
        );
        registerSchedulerMetrics(
            "external",
            schedulerConfig.externalScheduler(schedulerConfig.externalSchedulerProperties())
        );
    }

    private void registerSchedulerMetrics(String name, Scheduler scheduler) {
        Gauge.builder("scheduler_pool_size", scheduler, this::getPoolSize)
            .tag("scheduler", name)
            .description("Current pool size")
            .register(meterRegistry);

        Gauge.builder("scheduler_queue_size", scheduler, this::getQueueSize)
            .tag("scheduler", name)
            .description("Current queue size")
            .register(meterRegistry);

        Gauge.builder("scheduler_active_threads", scheduler, this::getActiveThreads)
            .tag("scheduler", name)
            .description("Active threads")
            .register(meterRegistry);
    }

    private double getPoolSize(Scheduler scheduler) {
        try {
            Field field = scheduler.getClass().getDeclaredField("executor");
            field.setAccessible(true);
            ThreadPoolExecutor executor = (ThreadPoolExecutor) field.get(scheduler);
            return executor.getPoolSize();
        } catch (Exception e) {
            return 0;
        }
    }

    private double getQueueSize(Scheduler scheduler) {
        try {
            Field field = scheduler.getClass().getDeclaredField("executor");
            field.setAccessible(true);
            ThreadPoolExecutor executor = (ThreadPoolExecutor) field.get(scheduler);
            return executor.getQueue().size();
        } catch (Exception e) {
            return 0;
        }
    }

    private double getActiveThreads(Scheduler scheduler) {
        try {
            Field field = scheduler.getClass().getDeclaredField("executor");
            field.setAccessible(true);
            ThreadPoolExecutor executor = (ThreadPoolExecutor) field.get(scheduler);
            return executor.getActiveCount();
        } catch (Exception e) {
            return 0;
        }
    }
}
