package cn.tnar.parking.netty.handler;

import cn.tnar.parking.aspect.MetricsAspect;
import cn.tnar.parking.model.CarOut;
import cn.tnar.parking.model.Respond;
import cn.tnar.parking.model.Transmission;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * CarOutHandler 单元测试
 */
class CarOutHandlerTest {

    private CarOutHandler handler;
    private MetricsAspect metricsAspect;

    @BeforeEach
    void setUp() {
        metricsAspect = new MetricsAspect(new SimpleMeterRegistry());
        handler = new CarOutHandler(metricsAspect);
    }

    @Test
    void testHandle_Success() {
        // Given
        CarOut carOut = CarOut.newBuilder()
                .setPlateNo("陕 A12345")
                .setChannel("EXIT-01")
                .setOutTime(System.currentTimeMillis() / 1000)
                .setDuration(3600)
                .build();
        Transmission transmission = Transmission.newBuilder()
                .setID(1004)
                .setType(Transmission.MessageType.TYPE_CAR_OUT)
                .setMessage(carOut.toByteString())
                .build();
        String parkCode = "PARK001";

        // When & Then
        reactor.core.publisher.Mono<Respond> result = handler.handle(transmission, parkCode);

        StepVerifier.create(result)
            .assertNext(respond -> {
                assertEquals(Respond.RespondCode.CODE_SUCCESS, respond.getCode());
            })
            .verifyComplete();
    }

    @Test
    void testProcessedType() {
        // When & Then
        assertEquals(Transmission.MessageType.TYPE_CAR_OUT, handler.processedType());
    }
}
