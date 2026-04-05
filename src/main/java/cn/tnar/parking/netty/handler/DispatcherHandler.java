package cn.tnar.parking.netty.handler;

import cn.tnar.parking.config.SchedulerConfig;
import cn.tnar.parking.model.Respond;
import cn.tnar.parking.model.RespondProto;
import cn.tnar.parking.model.Transmission;
import cn.tnar.parking.netty.ChannelMaintainer;
import cn.tnar.parking.task.HandlerRegistry;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;

/**
 * 消息分派处理器
 * 负责将消息分派给对应的 Handler 处理，并统一处理异常
 */
@Slf4j
@Component
@ChannelHandler.Sharable
public class DispatcherHandler extends SimpleChannelInboundHandler<Transmission> {

    private final HandlerRegistry handlerRegistry;
    private final Scheduler databaseScheduler;
    private static final int TIMEOUT_SECONDS = 10;

    public DispatcherHandler(
        HandlerRegistry handlerRegistry,
        SchedulerConfig schedulerConfig) {
        this.handlerRegistry = handlerRegistry;
        this.databaseScheduler = schedulerConfig.databaseScheduler(schedulerConfig.databaseSchedulerProperties());
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Transmission msg) throws Exception {
        String parkCode = ctx.channel().attr(ChannelMaintainer.PARK_CODE_ATTR).get();
        log.debug("[MSG_IN] parkCode={}, type={}, id={}", parkCode, msg.getType(), msg.getID());

        if (!Transmission.MessageType.TYPE_RESPOND.equals(msg.getType())) {
            Handler handler = handlerRegistry.getHandler(msg.getType());
            log.debug("[DISPATCH] parkCode={}, type={}, handler={}", parkCode, msg.getType(),
                handler != null ? handler.getClass().getSimpleName() : "null");

            Mono<Respond> responseMono;
            if (handler == null) {
                log.warn("[WARN] 未找到处理器，parkCode={}, messageType={}", parkCode, msg.getType());
                responseMono = Mono.just(RespondProto.success());
            } else {
                responseMono = handler.handle(msg, parkCode);
            }

            responseMono
                .timeout(Duration.ofSeconds(TIMEOUT_SECONDS),
                    Mono.just(RespondProto.failure("处理超时")))
                .subscribeOn(databaseScheduler)
                .publishOn(Schedulers.immediate())
                .subscribe(
                    respond -> {
                        // 切换回 EventLoop 线程执行写操作，确保经过编码器
                        ctx.executor().execute(() -> {
                            if (ctx.channel().isActive()) {
                                // 使用 channel().writeAndFlush() 而不是 ctx.writeAndFlush()，
                                // 确保从管道头部开始传递，经过所有编码器
                                ctx.channel().writeAndFlush(Transmission.newBuilder()
                                    .setID(msg.getID())
                                    .setType(Transmission.MessageType.TYPE_RESPOND)
                                    .setMessage(respond.toByteString())
                                    .build());
                            }
                        });
                    },
                    error -> {
                        log.error("[ERROR] 处理消息失败，parkCode={}, type={}, id={}, error={}",
                            parkCode, msg.getType(), msg.getID(), error.getMessage());

                        Respond errorRespond = RespondProto.failure("系统异常");
                        Transmission errorResponse = Transmission.newBuilder()
                            .setID(msg.getID())
                            .setType(Transmission.MessageType.TYPE_RESPOND)
                            .setMessage(errorRespond.toByteString())
                            .build();

                        if (ctx.channel().isActive()) {
                            ctx.channel().writeAndFlush(errorResponse);
                        }
                    }
                );
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        ChannelMaintainer.remove(ctx.channel());
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
