package cn.tnar.parking.integration;

import cn.tnar.parking.aspect.MetricsAspect;
import cn.tnar.parking.config.AuthProperties;
import cn.tnar.parking.config.SchedulerConfig;
import cn.tnar.parking.framework.IdempotencyGuard;
import cn.tnar.parking.model.Authenticate;
import cn.tnar.parking.model.Respond;
import cn.tnar.parking.model.RespondProto;
import cn.tnar.parking.model.Transmission;
import cn.tnar.parking.netty.handler.AuthHandler;
import cn.tnar.parking.netty.handler.DispatcherHandler;
import cn.tnar.parking.repository.auth.InMemoryAuthRepository;
import cn.tnar.parking.repository.auth.ReactiveAuthRepository;
import cn.tnar.parking.task.HandlerRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.timeout.IdleStateHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 认证集成测试
 */
@ExtendWith(MockitoExtension.class)
class AuthIntegrationTest {

    @Mock
    private HandlerRegistry handlerRegistry;

    private ReactiveAuthRepository authRepository;
    private AuthHandler authHandler;
    private DispatcherHandler dispatcherHandler;
    private SimpleMeterRegistry registry;
    private MetricsAspect metricsAspect;
    private SchedulerConfig schedulerConfig;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        metricsAspect = new MetricsAspect(registry);
        schedulerConfig = new SchedulerConfig();

        // 创建测试用认证配置
        AuthProperties properties = new AuthProperties();
        AuthProperties.AuthEntry entry = new AuthProperties.AuthEntry();
        entry.setSerial("TEST-001");
        entry.setParkCode("PARK001");
        entry.setSecret("test-secret");
        properties.getEntries().add(entry);

        authRepository = new InMemoryAuthRepository(properties);
        authHandler = new AuthHandler(authRepository, metricsAspect);
        dispatcherHandler = new DispatcherHandler(handlerRegistry, schedulerConfig, new IdempotencyGuard());
    }

    @Test
    void testValidate_Success() {
        // When & Then
        assertTrue(authRepository.validate("TEST-001", "test-secret").block());
    }

    @Test
    void testValidate_Failure_WrongSecret() {
        // When & Then
        assertFalse(authRepository.validate("TEST-001", "wrong-secret").block());
    }

    @Test
    void testValidate_Failure_UnknownSerial() {
        // When & Then
        assertFalse(authRepository.validate("UNKNOWN-001", "test-secret").block());
    }

    @Test
    void testFindBySerial_Success() {
        // When
        cn.tnar.parking.repository.auth.AuthInfo authInfo = authRepository.findBySerial("TEST-001").block();

        // Then
        assertNotNull(authInfo);
        assertEquals("PARK001", authInfo.getParkCode());
        assertEquals("test-secret", authInfo.getSecret());
    }

    @Test
    void testFindBySerial_Failure() {
        // When
        cn.tnar.parking.repository.auth.AuthInfo authInfo = authRepository.findBySerial("UNKNOWN-001").block();

        // Then
        assertNull(authInfo);
    }
}
