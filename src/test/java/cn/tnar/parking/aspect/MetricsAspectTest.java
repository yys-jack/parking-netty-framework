package cn.tnar.parking.aspect;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MetricsAspectTest {

    private MetricsAspect aspect;
    private MeterRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        aspect = new MetricsAspect(registry);
    }

    @Test
    void testRecordAuthSuccess() {
        aspect.recordAuthSuccess("PARK001", 0.05);

        assertEquals(1, registry.counter("parking_netty_auth_success_total", "park_code", "PARK001").count());
    }

    @Test
    void testRecordAuthFailure() {
        aspect.recordAuthFailure("PARK001", "invalid_secret", 0.03);

        assertEquals(1, registry.counter("parking_netty_auth_failure_total",
            "park_code", "PARK001", "reason", "invalid_secret").count());
    }

    @Test
    void testRecordMessage() {
        aspect.recordMessage("CAR_IN", "PARK001", 0.1, true);

        assertEquals(1, registry.counter("parking_netty_messages_total",
            "message_type", "CAR_IN", "park_code", "PARK001").count());
    }

    @Test
    void testRecordMessageFailure() {
        aspect.recordMessage("CAR_OUT", "PARK002", 0.08, false);

        assertEquals(1, registry.counter("parking_netty_messages_total",
            "message_type", "CAR_OUT", "park_code", "PARK002").count());
        assertEquals(1, registry.counter("parking_netty_messages_failure_total",
            "message_type", "CAR_OUT", "park_code", "PARK002").count());
    }
}
