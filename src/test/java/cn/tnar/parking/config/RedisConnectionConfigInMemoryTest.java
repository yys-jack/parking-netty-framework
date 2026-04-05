package cn.tnar.parking.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.test.context.TestPropertySource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RedisConnectionConfig 条件化配置测试
 * <p>
 * 验证当 type=in-memory 时，Redis 连接不会被初始化
 * </p>
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "server.port=0",
        "netty.tcp.port=0",
        "auth.storage.type=in-memory"
})
class RedisConnectionConfigInMemoryTest {

    @Autowired
    private ApplicationContext context;

    /**
     * 验证当 type=in-memory 时，RedisConnectionConfig 不被加载
     */
    @Test
    void whenTypeIsInMemory_thenRedisConnectionConfigNotLoaded() {
        assertThat(context.containsBean("redisConnectionConfig")).isFalse();
    }

    /**
     * 验证当 type=in-memory 时，RedisTemplate bean 不被创建
     */
    @Test
    void whenTypeIsInMemory_thenRedisTemplateNotCreated() {
        String[] beanNames = context.getBeanNamesForType(org.springframework.data.redis.core.RedisTemplate.class);
        assertThat(beanNames).isEmpty();
    }

    /**
     * 验证当 type=in-memory 时，ReactiveRedisTemplate bean 不被创建
     */
    @Test
    void whenTypeIsInMemory_thenReactiveRedisTemplateNotCreated() {
        String[] beanNames = context.getBeanNamesForType(org.springframework.data.redis.core.ReactiveRedisTemplate.class);
        assertThat(beanNames).isEmpty();
    }
}
