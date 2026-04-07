package cn.tnar.parking.repository.auth;

import cn.tnar.parking.config.AuthJdbcProperties;
import cn.tnar.parking.config.AuthRedisProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import jakarta.annotation.PostConstruct;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;

@Component
@ConditionalOnProperty(prefix = "auth.storage", name = "type", havingValue = "redis")
public class RedisAuthRepository implements ReactiveAuthRepository {

    private static final Logger log = Logger.getLogger(RedisAuthRepository.class.getName());

    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private final JdbcTemplate jdbcTemplate;
    private final AuthRedisProperties redisProperties;
    private final AuthJdbcProperties jdbcProperties;
    private final String keyPrefix;

    public RedisAuthRepository(
            ReactiveRedisTemplate<String, String> redisTemplate,
            JdbcTemplate jdbcTemplate,
            AuthRedisProperties redisProperties,
            AuthJdbcProperties jdbcProperties) {
        this.redisTemplate = redisTemplate;
        this.jdbcTemplate = jdbcTemplate;
        this.redisProperties = redisProperties;
        this.jdbcProperties = jdbcProperties;
        this.keyPrefix = redisProperties.getKeyPrefix();
    }

    @PostConstruct
    public void init() {
        String sql = "SELECT serial, park_code, secret FROM auth_info WHERE enabled = 1";
        List<AuthInfo> authInfos = jdbcTemplate.query(sql, (rs, rowNum) ->
            new AuthInfo(rs.getString("serial"), rs.getString("park_code"), rs.getString("secret"))
        );

        // 使用响应式批量写入，减少 Redis RTT
        Flux.fromIterable(authInfos)
            .flatMap(info -> {
                String key = keyPrefix + info.getSerial();
                return redisTemplate.opsForHash().put(key, "parkCode", info.getParkCode())
                    .then(redisTemplate.opsForHash().put(key, "secret", info.getSecret()));
            }, 10) // 并发度 10，避免一次性创建过多并发请求
            .then()
            .block(); // 启动时阻塞完成

        log.info("Loaded " + authInfos.size() + " auth entries to Redis with batched write");
    }

    @Override
    public Mono<AuthInfo> findBySerial(String serial) {
        String key = keyPrefix + serial;
        return redisTemplate
                .opsForHash()
                .multiGet(key, Arrays.asList("parkCode", "secret"))
                .flatMap(values -> {
                    if (values == null || values.isEmpty()) {
                        return Mono.empty();
                    }
                    String parkCode = (String) values.get(0);
                    String secret = (String) values.get(1);
                    if (parkCode == null || secret == null) {
                        return Mono.empty();
                    }
                    return Mono.just(new AuthInfo(serial, parkCode, secret));
                });
    }

    @Override
    public Mono<Boolean> validate(String serial, String secret) {
        return findBySerial(serial)
                .map(info -> info != null && info.getSecret().equals(secret));
    }

    /**
     * Refresh cache from database (called by admin API)
     */
    public Mono<Integer> refreshCache() {
        return Mono.fromCallable(() -> {
                    String sql = "SELECT serial, park_code, secret FROM auth_info WHERE enabled = 1";
                    return jdbcTemplate.query(sql, (rs, rowNum) ->
                            new AuthInfo(rs.getString("serial"), rs.getString("park_code"), rs.getString("secret"))
                    );
                })
                .flatMap(authInfos ->
                    Flux.fromIterable(authInfos)
                        .flatMap(info -> {
                            String key = keyPrefix + info.getSerial();
                            return redisTemplate.opsForHash().put(key, "parkCode", info.getParkCode())
                                .then(redisTemplate.opsForHash().put(key, "secret", info.getSecret()));
                        }, 10)
                        .then()
                        .thenReturn(authInfos.size())
                );
    }
}
