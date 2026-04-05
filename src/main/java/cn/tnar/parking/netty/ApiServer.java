package cn.tnar.parking.netty;

import cn.tnar.parking.aspect.MetricsAspect;
import cn.tnar.parking.config.NettyProperties;
import cn.tnar.parking.netty.handler.AuthHandler;
import cn.tnar.parking.netty.handler.DispatcherHandler;
import cn.tnar.parking.netty.handler.IdleDisconnectHandler;
import cn.tnar.parking.model.Transmission;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.handler.codec.protobuf.ProtobufDecoder;
import io.netty.handler.codec.protobuf.ProtobufEncoder;
import io.netty.handler.timeout.IdleStateHandler;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.nio.ByteOrder;

/**
 * Netty API 服务器
 */
@Component
@Slf4j
public class ApiServer {

    private Channel serverChannel;
    private NioEventLoopGroup boss;
    private NioEventLoopGroup worker;

    private final NettyProperties nettyProperties;
    private final AuthHandler authHandler;
    private final DispatcherHandler dispatcherHandler;
    private final MetricsAspect metricsAspect;
    private final MeterRegistry meterRegistry;

    public ApiServer(NettyProperties nettyProperties, AuthHandler authHandler, DispatcherHandler dispatcherHandler,
                     MetricsAspect metricsAspect, MeterRegistry meterRegistry) {
        this.nettyProperties = nettyProperties;
        this.authHandler = authHandler;
        this.dispatcherHandler = dispatcherHandler;
        this.metricsAspect = metricsAspect;
        this.meterRegistry = meterRegistry;

        // 注册连接数指标
        Gauge.builder("parking_netty_connections_active",
                () -> (double) ChannelMaintainer.getActiveCount())
            .description("Active TCP connections")
            .register(meterRegistry);

        // 设置连接回调用于指标收集
        ChannelMaintainer.setConnectionCallback(parkCode ->
            meterRegistry.counter("parking_netty_connections_total").increment()
        );
        ChannelMaintainer.setDisconnectionCallback(parkCode ->
            meterRegistry.counter("parking_netty_disconnections_total").increment()
        );
    }

    @PostConstruct
    public void init() {
        NioEventLoopGroup boss = new NioEventLoopGroup();
        NioEventLoopGroup worker = new NioEventLoopGroup();

        ChannelFuture f = null;
        try {
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(boss, worker)
                    .channel(NioServerSocketChannel.class)
                    .option(ChannelOption.SO_BACKLOG, nettyProperties.getBacklog())
                    .childOption(ChannelOption.SO_KEEPALIVE, true)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) throws Exception {
                            ch.pipeline()
                                    // 前 4 字节小端存 protobuf 的字节长度长度
                                    .addLast(new LengthFieldBasedFrameDecoder(ByteOrder.LITTLE_ENDIAN, 512 << 10, 0, 4, 0, 4, true))
                                    // 解码 protobuf
                                    .addLast(new ProtobufDecoder(Transmission.getDefaultInstance()))
                                    // 验证发来的验证消息
                                    .addLast("auth-check", authHandler)
                                    // 未发验证 10s 内自动关闭链接
                                    .addLast("auth-timeout", new IdleStateHandler(nettyProperties.getAuthTimeout(), 0, 0))
                                    .addLast("auth-disconnect", new IdleDisconnectHandler())
                                    // 90s 内没有心跳自动关闭
                                    .addLast(new IdleStateHandler(nettyProperties.getHeartbeatInterval(), 0, 0))
                                    .addLast(dispatcherHandler)
                                    // 字节前 4 位小端写入编码长度
                                    .addLast(new LengthFieldPrepender(ByteOrder.LITTLE_ENDIAN, 4, 0, false))
                                    // 编码 protobuf
                                    .addLast(new ProtobufEncoder());
                        }
                    });
            f = bootstrap.bind(nettyProperties.getPort()).syncUninterruptibly();
            serverChannel = f.channel();
            this.boss = boss;
            this.worker = worker;
        } finally {
            if (f != null && f.isSuccess()) {
                log.info("TCP API server started on port {}", nettyProperties.getPort());
            } else {
                log.error("TCP API server start failed");
            }
        }
    }

    @PreDestroy
    public void stop() {
        log.info("TCP API server shutting down...");
        if (serverChannel != null) {
            serverChannel.close();
        }
        worker.shutdownGracefully();
        boss.shutdownGracefully();
    }
}
