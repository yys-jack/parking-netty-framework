package cn.tnar.parking.netty.handler;

import cn.tnar.parking.aspect.MetricsAspect;
import cn.tnar.parking.config.SchedulerConfig;
import cn.tnar.parking.config.SchedulerProperties;
import cn.tnar.parking.framework.IdempotencyGuard;
import cn.tnar.parking.model.Respond;
import cn.tnar.parking.model.Transmission;
import cn.tnar.parking.netty.ChannelMaintainer;
import cn.tnar.parking.task.HandlerRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * DispatcherHandler 幂等性集成测试
 */
class DispatcherHandlerIdempotencyTest {

    private DispatcherHandler dispatcherHandler;
    private EmbeddedChannel channel;

    @Mock
    private HandlerRegistry handlerRegistry;

    @Mock
    private SchedulerConfig schedulerConfig;

    @Mock
    private SchedulerProperties schedulerProperties;

    private MetricsAspect metricsAspect;
    private AutoCloseable closeable;

    @BeforeEach
    void setUp() {
        closeable = MockitoAnnotations.openMocks(this);

        // 创建真实的 IdempotencyGuard 和 MetricsAspect
        IdempotencyGuard idempotencyGuard = new IdempotencyGuard();
        metricsAspect = new MetricsAspect(new SimpleMeterRegistry());

        // 模拟 Scheduler
        when(schedulerConfig.databaseScheduler(schedulerProperties))
            .thenReturn(reactor.core.scheduler.Schedulers.immediate());

        dispatcherHandler = new DispatcherHandler(handlerRegistry, schedulerConfig, idempotencyGuard, metricsAspect);
        channel = new EmbeddedChannel(dispatcherHandler);

        // 设置 parkCode
        channel.attr(ChannelMaintainer.PARK_CODE_ATTR).set("TEST-PARK-001");
    }

    @AfterEach
    void tearDown() throws Exception {
        channel.close();
        closeable.close();
    }

    @Test
    void firstMessage_shouldBeProcessed() {
        // Arrange
        Transmission msg = Transmission.newBuilder()
            .setID(100)
            .setType(Transmission.MessageType.TYPE_SPACE)
            .build();

        // Act
        channel.writeInbound(msg);

        // Assert - 消息应该被处理，无异常
        // 由于没有注册 Handler，应该返回成功响应
        Transmission response = channel.readOutbound();
        assertNotNull(response);
        assertEquals(100, response.getID());
        assertEquals(Transmission.MessageType.TYPE_RESPOND, response.getType());
    }

    @Test
    void duplicateMessage_shouldReturnDuplicateCode() throws Exception {
        // Arrange
        Transmission msg = Transmission.newBuilder()
            .setID(100)
            .setType(Transmission.MessageType.TYPE_SPACE)
            .build();

        // Act - 第一次发送
        channel.writeInbound(msg);
        Transmission firstResponse = channel.readOutbound();

        // Act - 重复发送相同 ID
        channel.writeInbound(msg);
        Transmission secondResponse = channel.readOutbound();

        // Assert - 第二次应该收到 CODE_DUPLICATE 响应
        assertNotNull(secondResponse);
        assertEquals(100, secondResponse.getID());
        assertEquals(Transmission.MessageType.TYPE_RESPOND, secondResponse.getType());

        Respond respond = Respond.parseFrom(secondResponse.getMessage());
        assertEquals(Respond.RespondCode.CODE_DUPLICATE, respond.getCode());
    }

    @Test
    void differentMessageId_shouldBeProcessed() throws Exception {
        // Arrange
        Transmission msg1 = Transmission.newBuilder()
            .setID(100)
            .setType(Transmission.MessageType.TYPE_SPACE)
            .build();

        Transmission msg2 = Transmission.newBuilder()
            .setID(101)
            .setType(Transmission.MessageType.TYPE_SPACE)
            .build();

        // Act - 发送第一条消息
        channel.writeInbound(msg1);
        Transmission response1 = channel.readOutbound();

        // Act - 发送不同 ID 的第二条消息
        channel.writeInbound(msg2);
        Transmission response2 = channel.readOutbound();

        // Assert - 两条消息都应该正常处理
        assertNotNull(response1);
        assertNotNull(response2);

        Respond respond1 = Respond.parseFrom(response1.getMessage());
        Respond respond2 = Respond.parseFrom(response2.getMessage());

        assertNotEquals(Respond.RespondCode.CODE_DUPLICATE, respond1.getCode());
        assertNotEquals(Respond.RespondCode.CODE_DUPLICATE, respond2.getCode());
    }
}
