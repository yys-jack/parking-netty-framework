package cn.tnar.parking.client;

import cn.tnar.parking.model.Authenticate;
import cn.tnar.parking.model.Respond;
import cn.tnar.parking.model.Transmission;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import lombok.extern.slf4j.Slf4j;

/**
 * 客户端认证处理器
 * 注意：继承 ChannelInboundHandlerAdapter 而非 SimpleChannelInboundHandler，
 * 因为我们需要手动控制消息引用释放，避免在 fireChannelRead 前消息被自动释放
 */
@Slf4j
public class ClientAuthHandler extends ChannelInboundHandlerAdapter {

    private final String serial;
    private final String secret;
    private boolean authenticated = false;

    public ClientAuthHandler(String serial, String secret) {
        this.serial = serial;
        this.secret = secret;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (!(msg instanceof Transmission)) {
            ctx.fireChannelRead(msg);
            return;
        }

        Transmission transmission = (Transmission) msg;
        log.info("ClientAuthHandler received message type: {}", transmission.getType());

        if (transmission.getType() == Transmission.MessageType.TYPE_RESPOND) {
            Respond respond = Respond.parseFrom(transmission.getMessage());
            log.info("Received response: code={}, number={}", respond.getCode(), respond.getCode().getNumber());

            if (!authenticated) {
                if (respond.getCode().getNumber() == 1) {
                    authenticated = true;
                    log.info("Authentication successful!");

                    // 认证成功后，移除认证处理器，添加业务处理器
                    ctx.pipeline().replace("auth","client",new ClientHandler());

                    // 重新触发消息处理，让 ClientHandler 处理这个响应
                    ctx.fireChannelRead(msg);
                } else {
                    log.error("Authentication failed, closing connection");
                    ctx.close();
                }
            }
        } else {
            // 非响应消息，传递给下游处理器
            ctx.fireChannelRead(msg);
        }
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        log.info("Channel active, sending authentication...");

        // 发送认证消息
        Authenticate authenticate = Authenticate.newBuilder()
                .setSerial(serial)
                .setSecret(secret)
                .build();

        Transmission authMsg = Transmission.newBuilder()
                .setID(1)
                .setType(Transmission.MessageType.TYPE_AUTHENTICATE)
                .setMessage(authenticate.toByteString())
                .build();

        log.info("Sending authentication: serial={}, secret={}", serial, secret);
        // 使用 channel().writeAndFlush() 让消息从管道头部开始传递，经过所有编码器
        ctx.channel().writeAndFlush(authMsg).addListener(future -> {
            if (future.isSuccess()) {
                log.info("Authentication message sent successfully");
            } else {
                log.error("Failed to send authentication message: {}", future.cause().getMessage(), future.cause());
            }
        });
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent) {
            IdleStateEvent event = (IdleStateEvent) evt;
            if (event.state() == IdleState.WRITER_IDLE) {
                // 发送心跳
                log.info("Sending heartbeat...");
                Transmission heartbeat = Transmission.newBuilder()
                        .setID(999)
                        .setType(Transmission.MessageType.TYPE_SPACE)
                        .setMessage(cn.tnar.parking.model.Space.newBuilder()
                                .setTotal(100)
                                .setRemained(50)
                                .build().toByteString())
                        .build();
                ctx.writeAndFlush(heartbeat);
            }
        }
    }
}
