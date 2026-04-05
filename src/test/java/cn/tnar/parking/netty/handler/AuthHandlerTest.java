package cn.tnar.parking.netty.handler;

import cn.tnar.parking.aspect.MetricsAspect;
import cn.tnar.parking.model.Authenticate;
import cn.tnar.parking.model.Transmission;
import cn.tnar.parking.netty.ChannelMaintainer;
import cn.tnar.parking.repository.auth.AuthInfo;
import cn.tnar.parking.repository.auth.ReactiveAuthRepository;
import io.netty.channel.embedded.EmbeddedChannel;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * AuthHandler 功能单元测试
 */
class AuthHandlerTest {

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
    void testAuthenticationSuccess() {
        // 准备测试数据
        String serial = "TEST-001";
        String secret = "test-secret";
        String parkCode = "PARK001";
        AuthInfo authInfo = new AuthInfo(serial, parkCode, secret);

        // 模拟 Repository 行为
        when(authRepository.validate(serial, secret)).thenReturn(Mono.just(true));
        when(authRepository.findBySerial(serial)).thenReturn(Mono.just(authInfo));

        // 构建认证消息
        Authenticate authenticate = Authenticate.newBuilder()
            .setSerial(serial)
            .setSecret(secret)
            .build();
        Transmission transmission = Transmission.newBuilder()
            .setType(Transmission.MessageType.TYPE_AUTHENTICATE)
            .setMessage(authenticate.toByteString())
            .setID(12345)
            .build();

        // 创建 EmbeddedChannel 用于测试
        EmbeddedChannel ctx = new EmbeddedChannel(authHandler);

        // 写入认证消息
        ctx.writeInbound(transmission);

        // 验证：认证成功指标被记录
        assertEquals(1.0, registry.counter("parking_netty_auth_success_total", "park_code", parkCode).count());
        verify(authRepository, times(1)).validate(serial, secret);
        verify(authRepository, times(1)).findBySerial(serial);

        ctx.close();
    }

    @Test
    void testAuthenticationFailure_InvalidCredentials() throws InterruptedException {
        String serial = "TEST-001";
        String secret = "wrong-secret";

        when(authRepository.validate(serial, secret)).thenReturn(Mono.just(false));

        Authenticate authenticate = Authenticate.newBuilder()
            .setSerial(serial)
            .setSecret(secret)
            .build();
        Transmission transmission = Transmission.newBuilder()
            .setType(Transmission.MessageType.TYPE_AUTHENTICATE)
            .setMessage(authenticate.toByteString())
            .setID(12345)
            .build();

        EmbeddedChannel ctx = new EmbeddedChannel(authHandler);
        ctx.writeInbound(transmission);

        // 等待异步处理完成
        Thread.sleep(100);

        // 验证：认证失败指标被记录
        assertEquals(1.0, registry.counter("parking_netty_auth_failure_total",
            "park_code", serial, "reason", "error: invalid credentials").count());
        verify(authRepository, times(1)).validate(serial, secret);
        verify(authRepository, never()).findBySerial(anyString());

        ctx.close();
    }

    @Test
    void testAuthenticationFailure_SerialNotFound() throws InterruptedException {
        String serial = "NOT-EXISTS";
        String secret = "test-secret";

        when(authRepository.validate(serial, secret)).thenReturn(Mono.just(true));
        when(authRepository.findBySerial(serial)).thenReturn(Mono.empty());

        Authenticate authenticate = Authenticate.newBuilder()
            .setSerial(serial)
            .setSecret(secret)
            .build();
        Transmission transmission = Transmission.newBuilder()
            .setType(Transmission.MessageType.TYPE_AUTHENTICATE)
            .setMessage(authenticate.toByteString())
            .setID(12345)
            .build();

        EmbeddedChannel ctx = new EmbeddedChannel(authHandler);
        ctx.writeInbound(transmission);

        // 等待异步处理完成
        Thread.sleep(100);

        // 验证：认证失败指标被记录（serial not found）
        assertEquals(1.0, registry.counter("parking_netty_auth_failure_total",
            "park_code", serial, "reason", "error: serial not found").count());

        ctx.close();
    }
}
