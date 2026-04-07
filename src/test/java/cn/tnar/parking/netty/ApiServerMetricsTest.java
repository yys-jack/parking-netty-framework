package cn.tnar.parking.netty;

import cn.tnar.parking.aspect.MetricsAspect;
import cn.tnar.parking.config.NettyProperties;
import cn.tnar.parking.netty.handler.AuthHandler;
import cn.tnar.parking.netty.handler.DispatcherHandler;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * ApiServer 连接指标测试
 */
class ApiServerMetricsTest {

    private MeterRegistry registry;
    private MetricsAspect metricsAspect;

    @Mock
    private NettyProperties nettyProperties;
    @Mock
    private AuthHandler authHandler;
    @Mock
    private DispatcherHandler dispatcherHandler;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        registry = new SimpleMeterRegistry();
        metricsAspect = new MetricsAspect(registry);
        // 清理 ChannelMaintainer 静态状态，避免测试间相互影响
        ChannelMaintainer.clearAll();
    }

    @Test
    void testApiServerRegistersConnectionGauge() {
        // 创建 ApiServer 会注册 Gauge
        new ApiServer(nettyProperties, authHandler, dispatcherHandler, metricsAspect, registry);

        // 初始连接数为 0
        assertEquals(0.0, registry.get("parking_netty_connections_active").gauge().value());

        // 模拟添加连接
        ChannelMaintainer.add("PARK001", new io.netty.channel.embedded.EmbeddedChannel());
        assertEquals(1.0, registry.get("parking_netty_connections_active").gauge().value());

        // 模拟移除连接
        ChannelMaintainer.remove(ChannelMaintainer.getChannel("PARK001"));
        assertEquals(0.0, registry.get("parking_netty_connections_active").gauge().value());
    }

    @Test
    void testConnectionCallbackIncrementsCounter() {
        // 创建 ApiServer 会设置回调
        new ApiServer(nettyProperties, authHandler, dispatcherHandler, metricsAspect, registry);

        // 模拟添加连接
        ChannelMaintainer.add("PARK001", new io.netty.channel.embedded.EmbeddedChannel());

        // 验证连接计数器
        assertEquals(1.0, registry.get("parking_netty_connections_total").counter().count());
    }

    @Test
    void testDisconnectionCallbackIncrementsCounter() {
        // 创建 ApiServer 会设置回调
        new ApiServer(nettyProperties, authHandler, dispatcherHandler, metricsAspect, registry);

        // 添加然后移除连接
        var channel = new io.netty.channel.embedded.EmbeddedChannel();
        ChannelMaintainer.add("PARK001", channel);
        ChannelMaintainer.remove(channel);

        // 验证断开计数器
        assertEquals(1.0, registry.get("parking_netty_disconnections_total").counter().count());
    }

    @Test
    void testGetActiveCount() {
        // 验证 ChannelMaintainer 的 getActiveCount 方法
        assertEquals(0, ChannelMaintainer.getActiveCount());

        ChannelMaintainer.add("PARK001", new io.netty.channel.embedded.EmbeddedChannel());
        assertEquals(1, ChannelMaintainer.getActiveCount());

        ChannelMaintainer.add("PARK002", new io.netty.channel.embedded.EmbeddedChannel());
        assertEquals(2, ChannelMaintainer.getActiveCount());
    }
}
