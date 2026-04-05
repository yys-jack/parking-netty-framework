package cn.tnar.parking.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import cn.tnar.parking.repository.auth.RedisAuthRepository;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RedisConnectionConfig 条件化配置测试
 * <p>
 * 验证当 type=redis 时，Redis 连接配置被正确加载
 * </p>
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties = {
        "server.port=0",
        "netty.tcp.port=0",
        "auth.storage.type=redis",
        "auth.redis.host=localhost",
        "auth.redis.port=6379"
})
class RedisConnectionConfigRedisTest {

    @Autowired
    private ApplicationContext context;

    // Mock RedisAuthRepository to avoid Redis connection requirement in tests
    @MockBean
    private RedisAuthRepository redisAuthRepository;

    /**
     * 验证当 type=redis 时，RedisConnectionConfig 被加载
     */
    @Test
    void whenTypeIsRedis_thenRedisConnectionConfigLoaded() {
        assertThat(context.containsBean("redisConnectionConfig")).isTrue();
    }

    /**
     * 验证当 type=redis 时，RedisTemplate bean 被创建
     */
    @Test
    void whenTypeIsRedis_thenRedisTemplateCreated() {
        String[] beanNames = context.getBeanNamesForType(RedisTemplate.class);
        assertThat(beanNames).isNotEmpty();
    }

    /**
     * 验证当 type=redis 时，ReactiveRedisTemplate bean 被创建
     */
    @Test
    void whenTypeIsRedis_thenReactiveRedisTemplateCreated() {
        String[] beanNames = context.getBeanNamesForType(ReactiveRedisTemplate.class);
        assertThat(beanNames).isNotEmpty();
    }

    /**
     * 验证当 type=redis 时，可以获取到 RedisTemplate 实例
     */
    @Test
    void whenTypeIsRedis_thenCanGetRedisTemplateBean() {
        // 使用具体的 bean 名称获取，避免多个 RedisTemplate bean 的歧义
        RedisTemplate<?, ?> redisTemplate = context.getBean("redisTemplate", RedisTemplate.class);
        assertThat(redisTemplate).isNotNull();
    }

    /**
     * 验证当 type=redis 时，可以获取到 ReactiveRedisTemplate 实例
     */
    @Test
    void whenTypeIsRedis_thenCanGetReactiveRedisTemplateBean() {
        // 使用具体的 bean 名称获取，避免多个 ReactiveRedisTemplate bean 的歧义
        ReactiveRedisTemplate<?, ?> reactiveRedisTemplate = context.getBean("reactiveRedisTemplate", ReactiveRedisTemplate.class);
        assertThat(reactiveRedisTemplate).isNotNull();
    }
}
