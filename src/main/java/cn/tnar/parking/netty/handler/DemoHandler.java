package cn.tnar.parking.netty.handler;

import cn.tnar.parking.framework.AbstractHandler;
import cn.tnar.parking.model.Respond;
import cn.tnar.parking.model.RespondProto;
import cn.tnar.parking.model.Transmission;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 演示处理器 - 示例 Handler 实现
 */
@Slf4j
@Component
public class DemoHandler extends AbstractHandler {

    @Override
    public Transmission.MessageType processedType() {
        return Transmission.MessageType.TYPE_DEMO;
    }

    @Override
    protected Respond doHandle(Transmission transmission, String parkCode) {
        log.info("DemoHandler 处理消息，parkCode: {}, ID: {}", parkCode, transmission.getID());
        log.info("消息内容：{}", transmission.getMessage().toStringUtf8());
        return RespondProto.success();
    }
}
