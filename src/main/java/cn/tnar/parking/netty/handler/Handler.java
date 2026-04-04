package cn.tnar.parking.netty.handler;

import cn.tnar.parking.model.Respond;
import cn.tnar.parking.model.Transmission;

import java.io.IOException;

/**
 * 消息处理器接口
 */
public interface Handler {

    Respond handle(Transmission transmission, String parkCode) throws IOException;

    Transmission.MessageType processedType();
}
