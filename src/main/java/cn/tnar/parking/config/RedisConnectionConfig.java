package cn.tnar.parking.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * 条件化 Redis 连接配置
 * <p>
 * 只有当 auth.storage.type=redis 时才启用 Redis 自动配置和创建连接
 * </p>
 */
@Configuration
@ConditionalOnProperty(prefix = "auth.storage", name = "type", havingValue = "redis")
@EnableConfigurationProperties(AuthRedisProperties.class)
@Import({RedisAutoConfiguration.class, RedisRepositoriesAutoConfiguration.class})
public class RedisConnectionConfig {
    // 此类仅用于按需启用 Redis 自动配置
    // RedisTemplate 和 ReactiveRedisTemplate 由 RedisAutoConfiguration 自动创建
}
