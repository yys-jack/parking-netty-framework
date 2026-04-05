package cn.tnar.parking.config;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MetricsConfigTest {

    @Test
    void testCommonTagsAreConfigured() {
        MetricsConfig config = new MetricsConfig();
        MeterRegistry registry = new SimpleMeterRegistry();

        config.commonTags().forEach((key, value) -> {
            assertNotNull(key);
            assertNotNull(value);
        });
    }
}
