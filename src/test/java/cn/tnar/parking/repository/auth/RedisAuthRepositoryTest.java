package cn.tnar.parking.repository.auth;

import cn.tnar.parking.config.AuthJdbcProperties;
import cn.tnar.parking.config.AuthRedisProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.test.StepVerifier;

@Testcontainers
class RedisAuthRepositoryTest {

    private GenericContainer<?> redisContainer;
    private MySQLContainer<?> mysqlContainer;
    private ReactiveRedisTemplate<String, String> redisTemplate;
    private JdbcTemplate jdbcTemplate;
    private RedisAuthRepository repository;

    @BeforeEach
    void setUp() {
        // Start Redis container
        redisContainer = new GenericContainer<>("redis:7-alpine")
                .withExposedPorts(6379);
        redisContainer.start();

        // Start MySQL container
        mysqlContainer = new MySQLContainer<>("mysql:8.0")
                .withDatabaseName("parking")
                .withUsername("root")
                .withPassword("test");
        mysqlContainer.start();

        // Redis setup
        LettuceConnectionFactory factory = new LettuceConnectionFactory(
                redisContainer.getHost(),
                redisContainer.getFirstMappedPort()
        );
        factory.afterPropertiesSet();
        RedisSerializationContext<String, String> serializationContext = RedisSerializationContext
                .<String, String>newSerializationContext(new StringRedisSerializer())
                .build();
        redisTemplate = new ReactiveRedisTemplate<>(factory, serializationContext);

        // MySQL setup
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                mysqlContainer.getJdbcUrl(),
                mysqlContainer.getUsername(),
                mysqlContainer.getPassword()
        );
        jdbcTemplate = new JdbcTemplate(dataSource);

        // Create table and insert test data
        jdbcTemplate.execute("CREATE TABLE auth_info (" +
                "id INT PRIMARY KEY AUTO_INCREMENT, " +
                "serial VARCHAR(64) NOT NULL UNIQUE, " +
                "park_code VARCHAR(64) NOT NULL, " +
                "secret VARCHAR(128) NOT NULL, " +
                "enabled TINYINT(1) DEFAULT 1" +
                ")");
        jdbcTemplate.update("INSERT INTO auth_info (serial, park_code, secret, enabled) VALUES (?, ?, ?, ?)",
                "TEST-001", "PARK001", "test-secret", 1);

        // Repository setup
        AuthRedisProperties redisProps = new AuthRedisProperties();
        redisProps.setKeyPrefix("parking:auth:");
        AuthJdbcProperties jdbcProps = new AuthJdbcProperties();
        repository = new RedisAuthRepository(redisTemplate, jdbcTemplate, redisProps, jdbcProps);

        // Initialize (synchronous, blocks until complete)
        repository.init();
    }

    @AfterEach
    void tearDown() {
        // Stop containers
        if (redisContainer != null && redisContainer.isRunning()) {
            redisContainer.stop();
        }
        if (mysqlContainer != null && mysqlContainer.isRunning()) {
            mysqlContainer.stop();
        }
    }

    @Test
    void findBySerial_whenExists_returnsAuthInfo() {
        StepVerifier.create(repository.findBySerial("TEST-001"))
                .assertNext(info -> {
                    assert info.getSerial().equals("TEST-001");
                    assert info.getParkCode().equals("PARK001");
                })
                .verifyComplete();
    }

    @Test
    void findBySerial_whenNotExists_returnsEmpty() {
        StepVerifier.create(repository.findBySerial("NON-EXISTENT"))
                .verifyComplete();  // Verifies Mono completes without emitting any value
    }

    @Test
    void validate_whenSecretMatches_returnsTrue() {
        StepVerifier.create(repository.validate("TEST-001", "test-secret"))
                .expectNext(true)
                .verifyComplete();
    }

    @Test
    void validate_whenSecretMismatches_returnsFalse() {
        StepVerifier.create(repository.validate("TEST-001", "wrong-secret"))
                .expectNext(false)
                .verifyComplete();
    }
}
