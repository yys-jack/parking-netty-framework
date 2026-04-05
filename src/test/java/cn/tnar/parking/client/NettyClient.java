package cn.tnar.parking.client;

import cn.tnar.parking.model.Authenticate;
import cn.tnar.parking.model.Transmission;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.handler.codec.protobuf.ProtobufDecoder;
import io.netty.handler.codec.protobuf.ProtobufEncoder;
import io.netty.handler.timeout.IdleStateHandler;
import lombok.extern.slf4j.Slf4j;

import java.nio.ByteOrder;
import java.util.concurrent.TimeUnit;

/**
 * Netty 客户端 - 用于测试连接服务器
 */
@Slf4j
public class NettyClient {

    private final String host;
    private final int port;
    private final String serial;
    private final String secret;

    private Channel clientChannel;
    private EventLoopGroup group;

    public NettyClient(String host, int port, String serial, String secret) {
        this.host = host;
        this.port = port;
        this.serial = serial;
        this.secret = secret;
    }

    public void connect() throws InterruptedException {
        group = new NioEventLoopGroup();

        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(group)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.TCP_NODELAY, true)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) throws Exception {
                        ch.pipeline()
                                // 帧解码器 - 前 4 字节小端存长度
                                .addLast(new LengthFieldBasedFrameDecoder(ByteOrder.LITTLE_ENDIAN, 512 << 10, 0, 4, 0, 4, true))
                                // Protobuf 解码
                                .addLast(new ProtobufDecoder(Transmission.getDefaultInstance()))
                                // 认证处理器 (认证成功后会移除自己并添加 ClientHandler)
                                .addLast("auth", new ClientAuthHandler(serial, secret))
                                // 心跳超时处理
                                .addLast(new IdleStateHandler(30, 0, 0))
                                // 帧编码器
                                .addLast(new LengthFieldPrepender(ByteOrder.LITTLE_ENDIAN, 4, 0, false))
                                // Protobuf 编码
                                .addLast(new ProtobufEncoder());
                    }
                });

        ChannelFuture f = bootstrap.connect(host, port).sync();
        clientChannel = f.channel();
        log.info("Connected to server at {}:{}", host, port);
    }

    public void sendCarInMessage(String plateNo, String laneChannel) {
        if (clientChannel != null && clientChannel.isActive()) {
            cn.tnar.parking.model.CarIn carIn = cn.tnar.parking.model.CarIn.newBuilder()
                    .setPlateNo(plateNo)
                    .setInTime(System.currentTimeMillis() / 1000)
                    .setChannel(laneChannel)
                    .build();
            Transmission transmission = Transmission.newBuilder()
                    .setID(1003)
                    .setType(Transmission.MessageType.TYPE_CAR_IN)
                    .setMessage(carIn.toByteString())
                    .build();
            this.clientChannel.writeAndFlush(transmission);
            log.info("Sent CarIn message: plate={}, channel={}", plateNo, laneChannel);
        }
    }

    public void sendDemoMessage() {
        if (clientChannel != null && clientChannel.isActive()) {
            Transmission transmission = Transmission.newBuilder()
                    .setID(1001)
                    .setType(Transmission.MessageType.TYPE_DEMO)
                    .setMessage(com.google.protobuf.ByteString.copyFromUtf8("Hello from client!"))
                    .build();
            clientChannel.writeAndFlush(transmission);
            log.info("Sent demo message to server");
        }
    }

    public void sendSpaceMessage(int total, int remained) {
        if (clientChannel != null && clientChannel.isActive()) {
            cn.tnar.parking.model.Space space = cn.tnar.parking.model.Space.newBuilder()
                    .setTotal(total)
                    .setRemained(remained)
                    .build();
            Transmission transmission = Transmission.newBuilder()
                    .setID(1002)
                    .setType(Transmission.MessageType.TYPE_SPACE)
                    .setMessage(space.toByteString())
                    .build();
            this.clientChannel.writeAndFlush(transmission);
            log.info("Sent space message: total={}, remained={}", total, remained);
        }
    }

    public void disconnect() {
        if (clientChannel != null) {
            clientChannel.close();
        }
        if (group != null) {
            group.shutdownGracefully();
        }
        log.info("Disconnected from server");
    }

    public static void main(String[] args) throws Exception {
        // 从参数或默认值获取配置
        String host = System.getProperty("client.host", "localhost");
        int port = Integer.parseInt(System.getProperty("client.port", "8889"));
        String serial = System.getProperty("client.serial", "TEST-001");
        String secret = System.getProperty("client.secret", "test-secret");

        NettyClient client = new NettyClient(host, port, serial, secret);

        try {
            client.connect();

            // 等待认证成功
            TimeUnit.SECONDS.sleep(2);

            // 发送测试消息
            client.sendDemoMessage();
            client.sendSpaceMessage(100, 50);
            client.sendCarInMessage("陕 A12345", "ENTRANCE-01");

            // 保持连接一段时间
            TimeUnit.SECONDS.sleep(90);

        } finally {
            client.disconnect();
        }
    }
}
