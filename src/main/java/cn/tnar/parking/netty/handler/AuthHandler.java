package cn.tnar.parking.netty.handler;

import cn.tnar.parking.aspect.MetricsAspect;
import cn.tnar.parking.model.Authenticate;
import cn.tnar.parking.model.Respond;
import cn.tnar.parking.model.RespondProto;
import cn.tnar.parking.model.Transmission;
import com.google.protobuf.InvalidProtocolBufferException;
import cn.tnar.parking.netty.ChannelMaintainer;
import cn.tnar.parking.repository.auth.ReactiveAuthRepository;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.SimpleChannelInboundHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * 认证处理器
 */
@Slf4j
@ChannelHandler.Sharable
@Component
public class AuthHandler extends SimpleChannelInboundHandler<Transmission> {

    private final ReactiveAuthRepository authRepository;
    private final MetricsAspect metricsAspect;

    public AuthHandler(ReactiveAuthRepository authRepository, MetricsAspect metricsAspect) {
        this.authRepository = authRepository;
        this.metricsAspect = metricsAspect;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Transmission msg) {
        Channel channel = ctx.channel();
        log.info("AuthHandler received message type: {}", msg.getType());

        if (msg.getType() != Transmission.MessageType.TYPE_AUTHENTICATE) {
            log.warn("Non-authenticate message received, closing connection");
            channel.close();
            return;
        }

        Authenticate authenticate;
        try {
            authenticate = Authenticate.parseFrom(msg.getMessage());
        } catch (InvalidProtocolBufferException e) {
            log.error("Failed to parse authenticate message", e);
            channel.close();
            return;
        }

        // Record auth start time
        long authStart = System.nanoTime();
        String serial = authenticate.getSerial();

        // Reactive authentication flow
        authRepository.validate(authenticate.getSerial(), authenticate.getSecret())
                .subscribe(
                        isValid -> handleValid(ctx, msg, authenticate, channel, authStart, serial),
                        error -> handleError(ctx, channel, error, authStart, serial)
                );
    }

    private void handleValid(ChannelHandlerContext ctx, Transmission msg,
                             Authenticate authenticate, Channel channel, long authStart, String serial) {
        authRepository.findBySerial(authenticate.getSerial())
                .subscribe(
                        authInfo -> {
                            long duration = System.nanoTime() - authStart;
                            double durationSeconds = duration / 1_000_000_000.0;

                            if (authInfo == null) {
                                log.error("Auth failed: serial not found");
                                metricsAspect.recordAuthFailure(serial, "serial_not_found", durationSeconds);
                                channel.close();
                                return;
                            }

                            String parkCode = authInfo.getParkCode();
                            metricsAspect.recordAuthSuccess(parkCode, durationSeconds);
                            ChannelMaintainer.add(parkCode, channel);
                            channel.attr(ChannelMaintainer.PARK_CODE_ATTR).setIfAbsent(parkCode);

                            Respond resp = RespondProto.success();
                            Transmission ret = Transmission.newBuilder()
                                    .setType(Transmission.MessageType.TYPE_RESPOND)
                                    .setID(msg.getID())
                                    .setMessage(resp.toByteString())
                                    .build();

                            channel.writeAndFlush(ret).addListener(future -> {
                                if (future.isSuccess()) {
                                    log.info("parkCode: {} auth success", parkCode);
                                } else {
                                    log.error("parkCode: {} auth success but response failed", parkCode);
                                }
                                removeAuthHandler(ctx.pipeline());
                            });
                        },
                        error -> {
                            long duration = System.nanoTime() - authStart;
                            double durationSeconds = duration / 1_000_000_000.0;
                            handleError(ctx, channel, error, authStart, serial);
                        }
                );
    }

    private void handleError(ChannelHandlerContext ctx, Channel channel, Throwable error, long authStart, String serial) {
        long duration = System.nanoTime() - authStart;
        double durationSeconds = duration / 1_000_000_000.0;
        log.error("Authentication error: " + error.getMessage());
        metricsAspect.recordAuthFailure(serial, "error: " + error.getMessage(), durationSeconds);
        channel.close();
    }

    private void removeAuthHandler(ChannelPipeline pipeline) {
        if (pipeline.get(this.getClass()) != null) {
            pipeline.remove(this);
        }
        if (pipeline.get("auth-timeout") != null) {
            pipeline.remove("auth-timeout");
        }
        if (pipeline.get("auth-disconnect") != null) {
            pipeline.remove("auth-disconnect");
        }
        log.info("AuthHandler removed");
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) {
        log.info("AuthHandler has been removed");
    }
}
