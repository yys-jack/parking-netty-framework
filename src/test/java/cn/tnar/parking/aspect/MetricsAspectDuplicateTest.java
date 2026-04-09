package cn.tnar.parking.aspect;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MetricsAspect 重复消息指标测试
 */
class MetricsAspectDuplicateTest {

    private MetricsAspect metricsAspect;
    private MeterRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        metricsAspect = new MetricsAspect(registry);
    }

    @Test
    void recordDuplicate_shouldIncrementCounter() {
        // Arrange
        String messageType = "TYPE_CAR_IN";
        String parkCode = "PARK001";

        // Act
        metricsAspect.recordDuplicate(messageType, parkCode);

        // Assert
        double count = registry.find("parking_netty_duplicates_total")
            .tag("message_type", messageType)
            .tag("park_code", parkCode)
            .counter()
            .count();
        assertEquals(1.0, count);
    }

    @Test
    void recordDuplicate_multipleTimes_shouldAccumulate() {
        // Arrange
        String messageType = "TYPE_CAR_OUT";
        String parkCode = "PARK002";

        // Act
        metricsAspect.recordDuplicate(messageType, parkCode);
        metricsAspect.recordDuplicate(messageType, parkCode);
        metricsAspect.recordDuplicate(messageType, parkCode);

        // Assert
        double count = registry.find("parking_netty_duplicates_total")
            .tag("message_type", messageType)
            .tag("park_code", parkCode)
            .counter()
            .count();
        assertEquals(3.0, count);
    }

    @Test
    void recordDuplicate_differentParkCode_shouldHaveSeparateCounters() {
        // Arrange
        String messageType = "TYPE_SPACE";

        // Act
        metricsAspect.recordDuplicate(messageType, "PARK001");
        metricsAspect.recordDuplicate(messageType, "PARK002");

        // Assert
        double count1 = registry.find("parking_netty_duplicates_total")
            .tag("message_type", messageType)
            .tag("park_code", "PARK001")
            .counter()
            .count();
        assertEquals(1.0, count1);

        double count2 = registry.find("parking_netty_duplicates_total")
            .tag("message_type", messageType)
            .tag("park_code", "PARK002")
            .counter()
            .count();
        assertEquals(1.0, count2);
    }
}
