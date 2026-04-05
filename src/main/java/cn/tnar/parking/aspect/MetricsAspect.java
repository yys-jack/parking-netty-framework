package cn.tnar.parking.aspect;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class MetricsAspect {

    private final MeterRegistry meterRegistry;

    public MetricsAspect(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    /**
     * 记录认证成功
     */
    public void recordAuthSuccess(String parkCode, double durationSeconds) {
        meterRegistry.counter("parking_netty_auth_success_total", "park_code", parkCode).increment();
        meterRegistry.timer("parking_netty_auth_duration_seconds", "park_code", parkCode)
            .record((long) (durationSeconds * 1000), TimeUnit.MILLISECONDS);
        log.debug("Recorded auth success for parkCode: {}, duration: {}s", parkCode, durationSeconds);
    }

    /**
     * 记录认证失败
     */
    public void recordAuthFailure(String parkCode, String reason, double durationSeconds) {
        meterRegistry.counter("parking_netty_auth_failure_total",
            "park_code", parkCode, "reason", reason).increment();
        log.debug("Recorded auth failure for parkCode: {}, reason: {}", parkCode, reason);
    }

    /**
     * 记录消息处理
     */
    public void recordMessage(String messageType, String parkCode, double durationSeconds, boolean success) {
        meterRegistry.counter("parking_netty_messages_total",
            "message_type", messageType, "park_code", parkCode).increment();

        if (!success) {
            meterRegistry.counter("parking_netty_messages_failure_total",
                "message_type", messageType, "park_code", parkCode).increment();
        }

        meterRegistry.timer("parking_netty_message_duration_seconds",
            "message_type", messageType, "park_code", parkCode)
            .record((long) (durationSeconds * 1000), TimeUnit.MILLISECONDS);
    }

    /**
     * 记录超时事件
     */
    public void recordTimeout(String messageType, String parkCode) {
        meterRegistry.counter("parking_netty_timeout_total",
            "message_type", messageType, "park_code", parkCode).increment();
        log.debug("Recorded timeout for messageType: {}, parkCode: {}", messageType, parkCode);
    }
}
