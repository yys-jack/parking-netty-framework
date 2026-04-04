package cn.tnar.parking.client;

import cn.tnar.parking.model.Respond;
import cn.tnar.parking.model.Transmission;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import lombok.extern.slf4j.Slf4j;

/**
 * 客户端业务处理器
 */
@Slf4j
public class ClientHandler extends SimpleChannelInboundHandler<Transmission> {

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Transmission msg) throws Exception {
        if (msg.getType() == Transmission.MessageType.TYPE_RESPOND) {
            Respond respond = Respond.parseFrom(msg.getMessage());
            log.info("Received response - ID: {}, Code: {}", msg.getID(), respond.getCode());

            // 根据响应码处理
            switch (respond.getCode()) {
                case CODE_SUCCESS:
                    log.info("Request succeeded");
                    break;
                case CODE_FAILURE:
                    log.error("Request failed");
                    break;
                case CODE_DUPLICATE:
                    log.warn("Duplicate request");
                    break;
                case CODE_TIMEOUT:
                    log.error("Request timeout");
                    break;
                default:
                    log.info("Unknown response code: {}", respond.getCode());
            }
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        log.info("Connection closed by server");
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        log.error("Exception occurred", cause);
        ctx.close();
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent) {
            IdleStateEvent event = (IdleStateEvent) evt;
            if (event.state() == IdleState.WRITER_IDLE) {
                log.info("Sending heartbeat...");
                Transmission heartbeat = Transmission.newBuilder()
                        .setID((int) (System.currentTimeMillis() / 1000))
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
