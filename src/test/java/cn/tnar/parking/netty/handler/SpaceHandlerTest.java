package cn.tnar.parking.netty.handler;

import cn.tnar.parking.aspect.MetricsAspect;
import cn.tnar.parking.model.Respond;
import cn.tnar.parking.model.Space;
import cn.tnar.parking.model.Transmission;
import cn.tnar.parking.repository.space.SpaceSnapshotAggregator;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * SpaceHandler 单元测试
 */
class SpaceHandlerTest {

    private SpaceHandler handler;
    private MetricsAspect metricsAspect;
    private SpaceSnapshotAggregator snapshotAggregator;

    @BeforeEach
    void setUp() {
        metricsAspect = new MetricsAspect(new SimpleMeterRegistry());
        snapshotAggregator = mock(SpaceSnapshotAggregator.class);
        handler = new SpaceHandler(metricsAspect, snapshotAggregator);
    }

    @Test
    void testHandle_Success() {
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

        // When & Then
        reactor.core.publisher.Mono<Respond> result = handler.handle(transmission, parkCode);

        StepVerifier.create(result)
            .assertNext(respond -> {
                assertEquals(Respond.RespondCode.CODE_SUCCESS, respond.getCode());
            })
            .verifyComplete();

        // Verify addSnapshot was called
        verify(snapshotAggregator, times(1)).addSnapshot(
            anyString(),
            anyInt(),
            anyInt(),
            anyLong()
        );
    }

    @Test
    void testProcessedType() {
        // When & Then
        assertEquals(Transmission.MessageType.TYPE_SPACE, handler.processedType());
    }
}
