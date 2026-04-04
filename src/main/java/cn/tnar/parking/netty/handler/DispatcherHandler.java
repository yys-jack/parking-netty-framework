package cn.tnar.parking.netty.handler;

import cn.tnar.parking.model.BusinessException;
import cn.tnar.parking.model.Respond;
import cn.tnar.parking.model.RespondProto;
import cn.tnar.parking.model.Transmission;
import cn.tnar.parking.task.HandlerRegistry;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 消息分派处理器
 * 负责将消息分派给对应的 Handler 处理，并统一处理异常
 */
@Slf4j
@Component
@ChannelHandler.Sharable
public class DispatcherHandler extends SimpleChannelInboundHandler<Transmission> {

    private final ExecutorService databaseExecutor = Executors.newFixedThreadPool(16);
    private final HandlerRegistry handlerRegistry;

    public DispatcherHandler(HandlerRegistry handlerRegistry) {
        this.handlerRegistry = handlerRegistry;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Transmission msg) throws Exception {
        String parkCode = ctx.channel().attr(cn.tnar.parking.netty.ChannelMaintainer.PARK_CODE_ATTR).get();
        log.debug("[MSG_IN] parkCode={}, type={}, id={}", parkCode, msg.getType(), msg.getID());

        if (!Transmission.MessageType.TYPE_RESPOND.equals(msg.getType())) {
            databaseExecutor.execute(() -> {
                Respond respond;
                try {
                    Handler handler = handlerRegistry.getHandler(msg.getType());
                    if (handler == null) {
                        log.warn("[WARN] 未找到处理器，parkCode={}, messageType={}", parkCode, msg.getType());
                        respond = RespondProto.success();
                    } else {
                        respond = handler.handle(msg, parkCode);
                    }
                } catch (BusinessException e) {
                    log.error("[ERROR] 业务异常，parkCode={}, type={}, id={}, message={}",
                            parkCode, msg.getType(), msg.getID(), e.getMessage());
                    respond = handleBusinessException(e);
                } catch (Exception e) {
                    log.error("[ERROR] 系统异常，parkCode={}, type={}, id={}, exception={}",
                            parkCode, msg.getType(), msg.getID(), e.getMessage());
                    respond = handleSystemException(e);
                }

                Transmission response = Transmission.newBuilder()
                        .setID(msg.getID())
                        .setType(Transmission.MessageType.TYPE_RESPOND)
                        .setMessage(respond.toByteString())
                        .build();

                ctx.executor().execute(() -> {
                    if (ctx.channel().isActive()) {
                        ctx.writeAndFlush(response);
                    }
                });
            });
        }
    }

    /**
     * 处理业务异常
     */
    private Respond handleBusinessException(BusinessException e) {
        return RespondProto.failure(e.getMessage());
    }

    /**
     * 处理系统异常
     */
    private Respond handleSystemException(Exception e) {
        return RespondProto.failure();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        cn.tnar.parking.netty.ChannelMaintainer.remove(ctx.channel());
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent) {
            IdleStateEvent idleStateEvent = (IdleStateEvent) evt;
            log.debug("[IDLE] state={}", idleStateEvent.state());
            if (idleStateEvent.state() == IdleState.READER_IDLE) {
                log.warn("[IDLE] READER_IDLE, closing channel={}", ctx.channel());
                ctx.channel().close();
            }
        } else {
            super.userEventTriggered(ctx, evt);
        }
    }
}
