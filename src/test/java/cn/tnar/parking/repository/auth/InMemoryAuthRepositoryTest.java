package cn.tnar.parking.repository.auth;

import cn.tnar.parking.config.AuthProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Collections;

class InMemoryAuthRepositoryTest {

    private InMemoryAuthRepository repository;

    @BeforeEach
    void setUp() {
        AuthProperties properties = new AuthProperties();
        AuthProperties.AuthEntry entry = new AuthProperties.AuthEntry();
        entry.setSerial("TEST-001");
        entry.setParkCode("PARK001");
        entry.setSecret("test-secret");
        properties.setEntries(Collections.singletonList(entry));

        repository = new InMemoryAuthRepository(properties);
    }

    @Test
    void findBySerial_whenExists_returnsAuthInfo() {
        Mono<AuthInfo> result = repository.findBySerial("TEST-001");

        StepVerifier.create(result)
                .assertNext(info -> {
                    assert info.getSerial().equals("TEST-001");
                    assert info.getParkCode().equals("PARK001");
                    assert info.getSecret().equals("test-secret");
                })
                .verifyComplete();
    }

    @Test
    void findBySerial_whenNotExists_returnsEmpty() {
        Mono<AuthInfo> result = repository.findBySerial("NON-EXISTENT");

        StepVerifier.create(result)
                .verifyComplete();
    }

    @Test
    void validate_whenSecretMatches_returnsTrue() {
        Mono<Boolean> result = repository.validate("TEST-001", "test-secret");

        StepVerifier.create(result)
                .expectNext(true)
                .verifyComplete();
    }

    @Test
    void validate_whenSecretMismatches_returnsFalse() {
        Mono<Boolean> result = repository.validate("TEST-001", "wrong-secret");

        StepVerifier.create(result)
                .expectNext(false)
                .verifyComplete();
    }
}
