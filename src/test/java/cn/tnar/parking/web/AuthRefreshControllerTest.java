package cn.tnar.parking.web;

import cn.tnar.parking.config.AuthStorageProperties;
import cn.tnar.parking.repository.auth.RedisAuthRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Collections;

import static org.mockito.Mockito.*;

class AuthRefreshControllerTest {

    private RedisAuthRepository mockRedisAuthRepository;
    private AuthStorageProperties authStorageProperties;
    private AuthRefreshController controller;

    @BeforeEach
    void setUp() {
        mockRedisAuthRepository = mock(RedisAuthRepository.class);
        authStorageProperties = new AuthStorageProperties();
        authStorageProperties.setAdminToken("test-admin-token");

        controller = new AuthRefreshController(authStorageProperties, mockRedisAuthRepository);
    }

    @Test
    void refresh_withInvalidToken_returnsUnauthorized() {
        Mono<ResponseEntity<RefreshResponse>> result = controller.refresh("wrong-token");

        StepVerifier.create(result)
                .assertNext(response -> {
                    assert response.getStatusCode() == HttpStatus.UNAUTHORIZED;
                    assert response.getBody() != null;
                    assert response.getBody().getCode() == 2;
                    assert response.getBody().getMessage().contains("Unauthorized");
                })
                .verifyComplete();
    }

    @Test
    void refresh_withValidToken_returnsSuccess() {
        when(mockRedisAuthRepository.refreshCache()).thenReturn(Mono.just(5));

        Mono<ResponseEntity<RefreshResponse>> result = controller.refresh("test-admin-token");

        StepVerifier.create(result)
                .assertNext(response -> {
                    assert response.getStatusCode() == HttpStatus.OK;
                    assert response.getBody() != null;
                    assert response.getBody().getCode() == 1;
                    assert response.getBody().getMessage().equals("success");
                    assert response.getBody().getData() != null;
                    assert response.getBody().getData().getRefreshed() == 5;
                })
                .verifyComplete();
    }

    @Test
    void refresh_withValidTokenButRefreshFails_returnsInternalServerError() {
        when(mockRedisAuthRepository.refreshCache()).thenReturn(Mono.error(new RuntimeException("Database connection failed")));

        Mono<ResponseEntity<RefreshResponse>> result = controller.refresh("test-admin-token");

        StepVerifier.create(result)
                .assertNext(response -> {
                    assert response.getStatusCode() == HttpStatus.INTERNAL_SERVER_ERROR;
                    assert response.getBody() != null;
                    assert response.getBody().getCode() == 2;
                    assert response.getBody().getMessage().contains("Database connection failed");
                })
                .verifyComplete();
    }
}
