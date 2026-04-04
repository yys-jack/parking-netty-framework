package cn.tnar.parking.task;

import cn.tnar.parking.netty.handler.Handler;
import cn.tnar.parking.model.Transmission;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 处理器注册表
 */
@Component
public class HandlerRegistry {

    private final Map<Transmission.MessageType, Handler> handlerMap = new HashMap<>(64);

    public HandlerRegistry(List<Handler> handlers) {
        handlers.forEach(h -> handlerMap.put(h.processedType(), h));
    }

    public Handler getHandler(Transmission.MessageType type) {
        return handlerMap.get(type);
    }
}
