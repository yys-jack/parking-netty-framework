package cn.tnar.parking.netty.handler;

import cn.tnar.parking.aspect.MetricsAspect;
import cn.tnar.parking.model.Respond;
import cn.tnar.parking.model.Transmission;
import com.google.protobuf.ByteString;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * DemoHandler 单元测试
 */
class DemoHandlerTest {

    private DemoHandler handler;
    private MetricsAspect metricsAspect;

    @BeforeEach
    void setUp() {
        metricsAspect = new MetricsAspect(new SimpleMeterRegistry());
        handler = new DemoHandler(metricsAspect);
    }

    @Test
    void testHandle_Success() {
        // Given
        Transmission transmission = Transmission.newBuilder()
                .setID(1001)
                .setType(Transmission.MessageType.TYPE_DEMO)
                .setMessage(ByteString.copyFromUtf8("Hello from client!"))
                .build();
        String parkCode = "PARK001";

        // When & Then
        Mono<Respond> result = handler.handle(transmission, parkCode);

        StepVerifier.create(result)
            .assertNext(respond -> {
                assertEquals(Respond.RespondCode.CODE_SUCCESS, respond.getCode());
            })
            .verifyComplete();
    }

    @Test
    void testProcessedType() {
        // When & Then
        assertEquals(Transmission.MessageType.TYPE_DEMO, handler.processedType());
    }
}
