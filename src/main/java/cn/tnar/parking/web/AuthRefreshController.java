package cn.tnar.parking.web;

import cn.tnar.parking.repository.auth.RedisAuthRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.logging.Logger;

@RestController
@RequestMapping("/api/auth")
@ConditionalOnProperty(prefix = "auth.storage", name = "type", havingValue = "redis")
public class AuthRefreshController {

    private static final Logger log = Logger.getLogger(AuthRefreshController.class.getName());

    private final String adminToken;
    private final RedisAuthRepository redisAuthRepository;

    public AuthRefreshController(
            cn.tnar.parking.config.AuthStorageProperties properties,
            RedisAuthRepository redisAuthRepository) {
        this.adminToken = properties.getAdminToken();
        this.redisAuthRepository = redisAuthRepository;
    }

    @PostMapping("/refresh")
    public Mono<ResponseEntity<RefreshResponse>> refresh(
            @RequestHeader("X-Admin-Token") String adminToken) {

        if (!this.adminToken.equals(adminToken)) {
            return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(RefreshResponse.error("Unauthorized: invalid admin token")));
        }

        return redisAuthRepository.refreshCache()
                .map(count -> ResponseEntity.ok(RefreshResponse.success(count)))
                .onErrorResume(ex -> {
                    log.severe("Failed to refresh cache: " + ex.getMessage());
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(RefreshResponse.error(ex.getMessage())));
                });
    }
}
