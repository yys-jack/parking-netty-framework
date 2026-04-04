package cn.tnar.parking.netty.handler;

import cn.tnar.parking.model.Respond;
import cn.tnar.parking.model.Transmission;
import com.google.protobuf.ByteString;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * DemoHandler 单元测试
 */
class DemoHandlerTest {

    private DemoHandler handler;

    @BeforeEach
    void setUp() {
        handler = new DemoHandler();
    }

    @Test
    void testHandle_Success() throws Exception {
        // Given
        Transmission transmission = Transmission.newBuilder()
                .setID(1001)
                .setType(Transmission.MessageType.TYPE_DEMO)
                .setMessage(ByteString.copyFromUtf8("Hello from client!"))
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
        assertEquals(Transmission.MessageType.TYPE_DEMO, type);
    }
}
