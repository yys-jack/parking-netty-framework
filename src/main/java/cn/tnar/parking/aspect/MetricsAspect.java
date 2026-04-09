package cn.tnar.parking.aspect;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class MetricsAspect {

    private final MeterRegistry meterRegistry;

    // 缓存常用指标，避免高频创建临时对象
    private final ConcurrentMap<String, Counter> authSuccessCounters = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Counter> authFailureCounters = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Counter> messageCounters = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Counter> messageFailureCounters = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Counter> timeoutCounters = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Timer> messageTimers = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Timer> authTimers = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Counter> duplicateCounters = new ConcurrentHashMap<>();

    public MetricsAspect(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    /**
     * 记录认证成功
     */
    public void recordAuthSuccess(String parkCode, double durationSeconds) {
        authSuccessCounters
            .computeIfAbsent(parkCode, k -> meterRegistry.counter("parking_netty_auth_success_total", "park_code", k))
            .increment();

        // 使用缓存的 Timer，避免每次创建新对象
        Timer timer = authTimers.computeIfAbsent(parkCode, k ->
            meterRegistry.timer("parking_netty_auth_duration_seconds", "park_code", k)
        );
        timer.record((long) (durationSeconds * 1000), TimeUnit.MILLISECONDS);

        log.debug("Recorded auth success for parkCode: {}, duration: {}s", parkCode, durationSeconds);
    }

    /**
     * 记录认证失败
     */
    public void recordAuthFailure(String parkCode, String reason, double durationSeconds) {
        authFailureCounters
            .computeIfAbsent(parkCode + ":" + reason, k -> meterRegistry.counter("parking_netty_auth_failure_total",
                "park_code", parkCode, "reason", reason))
            .increment();
        log.debug("Recorded auth failure for parkCode: {}, reason: {}", parkCode, reason);
    }

    /**
     * 记录消息处理
     */
    public void recordMessage(String messageType, String parkCode, double durationSeconds, boolean success) {
        String key = messageType + ":" + parkCode;
        messageCounters
            .computeIfAbsent(key, k -> meterRegistry.counter("parking_netty_messages_total",
                "message_type", messageType, "park_code", parkCode))
            .increment();

        if (!success) {
            messageFailureCounters
                .computeIfAbsent(key, k -> meterRegistry.counter("parking_netty_messages_failure_total",
                    "message_type", messageType, "park_code", parkCode))
                .increment();
        }

        // 使用缓存的 Timer，避免每次创建新对象
        Timer timer = messageTimers.computeIfAbsent(key, k ->
            meterRegistry.timer("parking_netty_message_duration_seconds",
                "message_type", messageType, "park_code", parkCode)
        );
        timer.record((long) (durationSeconds * 1000), TimeUnit.MILLISECONDS);
    }

    /**
     * 记录超时事件
     */
    public void recordTimeout(String messageType, String parkCode) {
        timeoutCounters
            .computeIfAbsent(messageType + ":" + parkCode, k -> meterRegistry.counter("parking_netty_timeout_total",
                "message_type", messageType, "park_code", parkCode))
            .increment();
        log.debug("Recorded timeout for messageType: {}, parkCode: {}", messageType, parkCode);
    }

    /**
     * 记录重复消息事件
     */
    public void recordDuplicate(String messageType, String parkCode) {
        String key = messageType + ":" + parkCode;
        duplicateCounters
            .computeIfAbsent(key, k -> meterRegistry.counter("parking_netty_duplicates_total",
                "message_type", messageType, "park_code", parkCode))
            .increment();
        log.debug("Recorded duplicate message: messageType={}, parkCode={}", messageType, parkCode);
    }
}
