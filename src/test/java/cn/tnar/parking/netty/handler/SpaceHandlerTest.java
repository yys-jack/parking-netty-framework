package cn.tnar.parking.netty.handler;

import cn.tnar.parking.aspect.MetricsAspect;
import cn.tnar.parking.framework.AbstractHandler;
import cn.tnar.parking.model.Respond;
import cn.tnar.parking.model.RespondProto;
import cn.tnar.parking.model.Space;
import cn.tnar.parking.model.Transmission;
import com.google.protobuf.ByteString;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * SpaceHandler 单元测试
 */
class SpaceHandlerTest {

    private SpaceHandler handler;
    private MetricsAspect metricsAspect;

    @BeforeEach
    void setUp() {
        metricsAspect = new MetricsAspect(new SimpleMeterRegistry());
        handler = new SpaceHandler(metricsAspect);
    }

    @Test
    void testHandle_Success() throws Exception {
        // Given
        Space space = Space.newBuilder()
                .setTotal(100)
                .setRemained(50)
                .build();
        Transmission transmission = Transmission.newBuilder()
                .setID(1001)
                .setType(Transmission.MessageType.TYPE_SPACE)
                .setMessage(space.toByteString())
                .build();
        String parkCode = "PARK001";

        // When
        Respond respond = handler.handle(transmission, parkCode);

        // Then
        assertNotNull(respond);
        assertEquals(Respond.RespondCode.CODE_SUCCESS, respond.getCode());
    }

    @Test
    void testHandle_ZeroRemained() throws Exception {
        // Given
        Space space = Space.newBuilder()
                .setTotal(100)
                .setRemained(0)
                .build();
        Transmission transmission = Transmission.newBuilder()
                .setID(1002)
                .setType(Transmission.MessageType.TYPE_SPACE)
                .setMessage(space.toByteString())
                .build();
        String parkCode = "PARK002";

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
        assertEquals(Transmission.MessageType.TYPE_SPACE, type);
    }
}
