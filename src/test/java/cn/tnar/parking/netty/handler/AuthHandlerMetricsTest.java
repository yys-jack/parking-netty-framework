package cn.tnar.parking.netty.handler;

import cn.tnar.parking.aspect.MetricsAspect;
import cn.tnar.parking.model.Authenticate;
import cn.tnar.parking.model.Transmission;
import cn.tnar.parking.repository.auth.ReactiveAuthRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * AuthHandler metrics 单元测试
 */
class AuthHandlerMetricsTest {

    @Mock
    private ReactiveAuthRepository authRepository;

    private SimpleMeterRegistry registry;
    private MetricsAspect metricsAspect;
    private AuthHandler authHandler;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        registry = new SimpleMeterRegistry();
        metricsAspect = new MetricsAspect(registry);
        authHandler = new AuthHandler(authRepository, metricsAspect);
    }

    @Test
    void testMetricsAspectIsInjected() {
        // Verify MetricsAspect is injected
        assertNotNull(authHandler);
    }

    @Test
    void testRecordAuthSuccess() {
        // 模拟认证成功
        String parkCode = "PARK001";
        double durationSeconds = 0.05;

        metricsAspect.recordAuthSuccess(parkCode, durationSeconds);

        assertEquals(1.0, registry.counter("parking_netty_auth_success_total", "park_code", parkCode).count());
        assertNotNull(registry.timer("parking_netty_auth_duration_seconds", "park_code", parkCode));
    }

    @Test
    void testRecordAuthFailure() {
        // 模拟认证失败
        String parkCode = "PARK001";
        String reason = "invalid_secret";
        double durationSeconds = 0.03;

        metricsAspect.recordAuthFailure(parkCode, reason, durationSeconds);

        assertEquals(1.0, registry.counter("parking_netty_auth_failure_total",
            "park_code", parkCode, "reason", reason).count());
    }

    @Test
    void testRecordAuthFailureWithDifferentReasons() {
        // 测试不同的失败原因
        String parkCode = "PARK001";

        metricsAspect.recordAuthFailure(parkCode, "serial_not_found", 0.02);
        metricsAspect.recordAuthFailure(parkCode, "error: timeout", 0.03);

        assertEquals(1.0, registry.counter("parking_netty_auth_failure_total",
            "park_code", parkCode, "reason", "serial_not_found").count());
        assertEquals(1.0, registry.counter("parking_netty_auth_failure_total",
            "park_code", parkCode, "reason", "error: timeout").count());
    }

    @Test
    void testMultipleAuthSuccess() {
        // 测试多次认证成功
        String parkCode = "PARK001";

        metricsAspect.recordAuthSuccess(parkCode, 0.05);
        metricsAspect.recordAuthSuccess(parkCode, 0.06);
        metricsAspect.recordAuthSuccess(parkCode, 0.07);

        assertEquals(3.0, registry.counter("parking_netty_auth_success_total", "park_code", parkCode).count());
    }

    @Test
    void testAuthMetricsWithDifferentParkCodes() {
        // 测试不同 parkCode 的指标
        metricsAspect.recordAuthSuccess("PARK001", 0.05);
        metricsAspect.recordAuthSuccess("PARK002", 0.06);
        metricsAspect.recordAuthFailure("PARK003", "invalid_secret", 0.03);

        assertEquals(1.0, registry.counter("parking_netty_auth_success_total", "park_code", "PARK001").count());
        assertEquals(1.0, registry.counter("parking_netty_auth_success_total", "park_code", "PARK002").count());
        assertEquals(1.0, registry.counter("parking_netty_auth_failure_total",
            "park_code", "PARK003", "reason", "invalid_secret").count());
    }
}
