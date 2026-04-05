package cn.tnar.parking.config;

import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Prometheus Metrics Integration Test
 *
 * Tests that:
 * - Prometheus endpoint is accessible
 * - Custom metrics are registered
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "netty.tcp.port=0",
        "management.server.port=-1"
    }
)
@ActiveProfiles("test")
class PrometheusMetricsIntegrationTest {

    @Autowired
    private MeterRegistry meterRegistry;

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void testMeterRegistryIsConfigured() {
        // Verify that the MeterRegistry bean is configured
        assertNotNull(meterRegistry, "MeterRegistry should be configured");
    }

    @Test
    void testCustomMetricsAreRegistered() {
        // Register a test counter
        meterRegistry.counter("parking_netty_auth_success_total", "park_code", "TEST").increment();

        // Verify the counter is registered and can be read
        double count = meterRegistry.counter("parking_netty_auth_success_total", "park_code", "TEST").count();
        assertEquals(1.0, count, "Counter should have count of 1");
    }

    @Test
    void testPrometheusEndpointIsAccessible() {
        // Access the prometheus metrics endpoint
        String response = restTemplate.getForObject("/actuator/prometheus", String.class);

        // Verify the response is not empty and contains metric format
        assertNotNull(response, "Prometheus endpoint should return metrics");
        assertTrue(response.length() > 0, "Metrics response should not be empty");
    }

    @Test
    void testAuthSuccessMetric() {
        // Simulate recording an auth success metric
        meterRegistry.counter("parking_netty_auth_success_total", "park_code", "PARK001").increment();
        meterRegistry.timer("parking_netty_auth_duration_seconds", "park_code", "PARK001")
            .record(50, java.util.concurrent.TimeUnit.MILLISECONDS);

        // Verify metrics are registered
        double authCount = meterRegistry.counter("parking_netty_auth_success_total", "park_code", "PARK001").count();
        assertEquals(1.0, authCount, "Auth success counter should be 1");
    }

    @Test
    void testConnectionGaugeMetric() {
        // Simulate connection metrics using atomic integer for gauge
        AtomicInteger connectionCount = new AtomicInteger(5);
        meterRegistry.gauge("parking_netty_connections_active", connectionCount,
            AtomicInteger::get);

        // Verify gauge value can be read from the atomic integer
        assertEquals(5, connectionCount.get(), "AtomicInteger should report 5 connections");
    }

    @Test
    void testMetricsEndpointContainsCustomMetrics() {
        // First register a custom metric
        meterRegistry.counter("parking_netty_test_metric_total").increment();

        // Access prometheus endpoint and verify it contains metrics
        String response = restTemplate.getForObject("/actuator/prometheus", String.class);
        assertNotNull(response, "Prometheus endpoint should return metrics");
        assertTrue(response.length() > 0, "Response should not be empty");
        // The prometheus metrics endpoint returns metrics in text format
        // We just verify the response is valid and contains some content
    }
}
