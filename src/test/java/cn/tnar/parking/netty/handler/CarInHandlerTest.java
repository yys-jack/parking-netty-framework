package cn.tnar.parking.netty.handler;

import cn.tnar.parking.aspect.MetricsAspect;
import cn.tnar.parking.model.CarIn;
import cn.tnar.parking.model.Respond;
import cn.tnar.parking.model.Transmission;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * CarInHandler 单元测试
 */
class CarInHandlerTest {

    private CarInHandler handler;
    private MetricsAspect metricsAspect;

    @BeforeEach
    void setUp() {
        metricsAspect = new MetricsAspect(new SimpleMeterRegistry());
        handler = new CarInHandler(metricsAspect);
    }

    @Test
    void testHandle_Success() throws Exception {
        // Given
        CarIn carIn = CarIn.newBuilder()
                .setPlateNo("陕 A12345")
                .setChannel("ENTRANCE-01")
                .setInTime(System.currentTimeMillis() / 1000)
                .build();
        Transmission transmission = Transmission.newBuilder()
                .setID(1003)
                .setType(Transmission.MessageType.TYPE_CAR_IN)
                .setMessage(carIn.toByteString())
                .build();
        String parkCode = "PARK001";

        // When
        Respond respond = handler.handle(transmission, parkCode);

        // Then
        assertNotNull(respond);
        assertEquals(Respond.RespondCode.CODE_SUCCESS, respond.getCode());
    }

    @Test
    void testProcessedType() {
        // When
        Transmission.MessageType type = handler.processedType();

        // Then
        assertEquals(Transmission.MessageType.TYPE_CAR_IN, type);
    }
}
