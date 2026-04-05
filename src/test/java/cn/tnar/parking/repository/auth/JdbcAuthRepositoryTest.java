package cn.tnar.parking.repository.auth;

import cn.tnar.parking.config.AuthJdbcProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.test.StepVerifier;

@Testcontainers
class JdbcAuthRepositoryTest {

    @Container
    static MySQLContainer<?> mysqlContainer = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("parking")
            .withUsername("root")
            .withPassword("test");

    private JdbcAuthRepository repository;
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                mysqlContainer.getJdbcUrl(),
                mysqlContainer.getUsername(),
                mysqlContainer.getPassword()
        );
        jdbcTemplate = new JdbcTemplate(dataSource);

        // Drop table if exists and recreate
        jdbcTemplate.execute("DROP TABLE IF EXISTS auth_info");
        jdbcTemplate.execute("CREATE TABLE auth_info (" +
                "id INT PRIMARY KEY AUTO_INCREMENT, " +
                "serial VARCHAR(64) NOT NULL UNIQUE, " +
                "park_code VARCHAR(64) NOT NULL, " +
                "secret VARCHAR(128) NOT NULL, " +
                "enabled TINYINT(1) DEFAULT 1" +
                ")");
        jdbcTemplate.update("INSERT INTO auth_info (serial, park_code, secret, enabled) VALUES (?, ?, ?, ?)",
                "TEST-001", "PARK001", "test-secret", 1);

        AuthJdbcProperties props = new AuthJdbcProperties();
        props.setTable("auth_info");
        repository = new JdbcAuthRepository(jdbcTemplate, props);
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS auth_info");
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
                .verifyComplete();
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
