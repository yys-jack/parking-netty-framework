package cn.tnar.parking.task;

import cn.tnar.parking.model.Respond;
import cn.tnar.parking.model.RespondProto;
import cn.tnar.parking.model.Transmission;
import cn.tnar.parking.netty.handler.Handler;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

/**
 * HandlerRegistry 测试
 */
class HandlerRegistryTest {

    @Test
    void testConstructorAndGetHandler() {
        // 创建一个测试 Handler
        Handler testHandler = new Handler() {
            @Override
            public Mono<Respond> handle(Transmission transmission, String parkCode) {
                return Mono.just(RespondProto.success());
            }

            @Override
            public Transmission.MessageType processedType() {
                return Transmission.MessageType.TYPE_DEMO;
            }
        };

        HandlerRegistry registry = new HandlerRegistry(Arrays.asList(testHandler));

        // 验证能获取到注册的 Handler
        Handler handler = registry.getHandler(Transmission.MessageType.TYPE_DEMO);
        assertNotNull(handler);
        assertSame(testHandler, handler);
    }

    @Test
    void testGetHandlerNotFound() {
        HandlerRegistry registry = new HandlerRegistry(Collections.emptyList());

        // 验证不存在的 Handler 返回 null
        Handler handler = registry.getHandler(Transmission.MessageType.TYPE_DEMO);
        assertNull(handler);
    }
}
