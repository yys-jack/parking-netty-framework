package cn.tnar.parking.netty.handler;

import cn.tnar.parking.model.CarOut;
import cn.tnar.parking.model.Respond;
import cn.tnar.parking.model.Transmission;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * CarOutHandler 单元测试
 */
class CarOutHandlerTest {

    private CarOutHandler handler;

    @BeforeEach
    void setUp() {
        handler = new CarOutHandler();
    }

    @Test
    void testHandle_Success() throws Exception {
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
        assertEquals(Transmission.MessageType.TYPE_CAR_OUT, type);
    }
}
