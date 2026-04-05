package cn.tnar.parking.config;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 调度器配置类
 * 提供数据库、外部 API、CPU 计算三种专用调度器
 */
@Configuration
public class SchedulerConfig {

    @Bean
    @ConfigurationProperties("scheduler.database")
    @Primary
    public SchedulerProperties databaseSchedulerProperties() {
        return new SchedulerProperties();
    }

    @Bean
    public Scheduler databaseScheduler(
        @org.springframework.beans.factory.annotation.Qualifier("databaseSchedulerProperties") SchedulerProperties properties) {
        return Schedulers.fromExecutor(
            new ThreadPoolExecutor(
                properties.getCoreThreads(),
                properties.getMaxThreads(),
                60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(properties.getQueueCapacity()),
                new ThreadFactoryBuilder()
                    .setNameFormat("db-scheduler-%d")
                    .setDaemon(true)
                    .build(),
                new ThreadPoolExecutor.CallerRunsPolicy()
            )
        );
    }

    @Bean
    @ConfigurationProperties("scheduler.external")
    public SchedulerProperties externalSchedulerProperties() {
        return new SchedulerProperties();
    }

    @Bean
    public Scheduler externalScheduler(
        @org.springframework.beans.factory.annotation.Qualifier("externalSchedulerProperties") SchedulerProperties properties) {
        return Schedulers.fromExecutor(
            new ThreadPoolExecutor(
                properties.getCoreThreads(),
                properties.getMaxThreads(),
                30L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(properties.getQueueCapacity()),
                new ThreadFactoryBuilder()
                    .setNameFormat("ext-scheduler-%d")
                    .setDaemon(true)
                    .build(),
                new ThreadPoolExecutor.CallerRunsPolicy()
            )
        );
    }

    @Bean
    public Scheduler cpuScheduler() {
        int cpuCores = Runtime.getRuntime().availableProcessors();
        return Schedulers.newBoundedElastic(cpuCores, cpuCores * 2, "cpu-scheduler");
    }
}
