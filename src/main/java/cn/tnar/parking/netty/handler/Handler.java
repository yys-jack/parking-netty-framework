package cn.tnar.parking.netty.handler;

import cn.tnar.parking.model.Respond;
import cn.tnar.parking.model.Transmission;
import reactor.core.publisher.Mono;

/**
 * 消息处理器接口
 */
public interface Handler {
    /**
     * 处理消息
     * @param transmission 传输消息
     * @param parkCode 停车场编码
     * @return 处理结果（响应式）
     */
    Mono<Respond> handle(Transmission transmission, String parkCode);

    /**
     * 返回此 Handler 处理的消息类型
     */
    Transmission.MessageType processedType();
}
