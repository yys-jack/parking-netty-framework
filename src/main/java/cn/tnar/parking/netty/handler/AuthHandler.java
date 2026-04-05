package cn.tnar.parking.netty.handler;

import cn.tnar.parking.model.Authenticate;
import cn.tnar.parking.model.Respond;
import cn.tnar.parking.model.RespondProto;
import cn.tnar.parking.model.Transmission;
import cn.tnar.parking.netty.ChannelMaintainer;
import cn.tnar.parking.repository.auth.ReactiveAuthRepository;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.SimpleChannelInboundHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 认证处理器
 */
@Slf4j
@ChannelHandler.Sharable
@Component
public class AuthHandler extends SimpleChannelInboundHandler<Transmission> {

    private final ReactiveAuthRepository authRepository;

    public AuthHandler(ReactiveAuthRepository authRepository) {
        this.authRepository = authRepository;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Transmission msg) throws Exception {
        Channel channel = ctx.channel();
        log.info("AuthHandler received message type: {}, is AUTHENTICATE: {}",
                msg.getType(), msg.getType() == Transmission.MessageType.TYPE_AUTHENTICATE);

        if (msg.getType() != Transmission.MessageType.TYPE_AUTHENTICATE) {
            log.warn("收到非认证消息，关闭连接：{}", msg.getType());
            channel.close();
        } else {
            Authenticate authenticate = Authenticate.parseFrom(msg.getMessage());
            log.info("authenticate : {}", authenticate);

            boolean isValid = authRepository.validate(authenticate.getSerial(), authenticate.getSecret()).block();

            if (!isValid) {
                log.error("认证失败：serial={}", authenticate.getSerial());
                channel.close();
                return;
            }

            cn.tnar.parking.repository.auth.AuthInfo authInfo = authRepository.findBySerial(authenticate.getSerial()).block();
            if (authInfo == null) {
                log.error("认证失败：serial={}", authenticate.getSerial());
                channel.close();
                return;
            }
            String parkCode = authInfo.getParkCode();

            ChannelMaintainer.add(parkCode, channel);
            channel.attr(ChannelMaintainer.PARK_CODE_ATTR).setIfAbsent(parkCode);

            Respond resp = RespondProto.success();
            Transmission ret = Transmission.newBuilder()
                    .setType(Transmission.MessageType.TYPE_RESPOND)
                    .setID(msg.getID())
                    .setMessage(resp.toByteString())
                    .build();

            // 先发送响应，再移除 Handler
            final String finalParkCode = parkCode;
            channel.writeAndFlush(ret).addListener(future -> {
                if (future.isSuccess()) {
                    log.info("parkCode : {}  认证成功，响应已发送", finalParkCode);
                } else {
                    log.error("parkCode : {}  认证成功，但响应发送失败", finalParkCode);
                }
            });

            ChannelPipeline pipeline = ctx.pipeline();
            if (pipeline.get(this.getClass()) != null) {
                pipeline.remove(this);
                pipeline.remove("auth-timeout");
                pipeline.remove("auth-disconnect");
            }
            log.info("parkCode : {}  认证成功，AuthHandler 已移除", parkCode);
        }
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        log.info("AuthHandler has been removed");
    }
}
