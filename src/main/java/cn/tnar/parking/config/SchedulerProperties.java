package cn.tnar.parking.config;

import lombok.Data;

/**
 * 调度器配置属性
 */
@Data
public class SchedulerProperties {
    private int coreThreads = 10;
    private int maxThreads = 20;
    private int queueCapacity = 1000;
    private int timeoutSeconds = 10;
}
