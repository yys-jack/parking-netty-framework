package cn.tnar.parking.repository.auth;

import cn.tnar.parking.config.AuthJdbcProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;

@Component
@ConditionalOnProperty(prefix = "auth.storage", name = "type", havingValue = "jdbc")
public class JdbcAuthRepository implements ReactiveAuthRepository {

    private final JdbcTemplate jdbcTemplate;
    private final String tableName;

    public JdbcAuthRepository(JdbcTemplate jdbcTemplate, AuthJdbcProperties properties) {
        this.jdbcTemplate = jdbcTemplate;
        this.tableName = properties.getTable();
    }

    @Override
    public Mono<AuthInfo> findBySerial(String serial) {
        return Mono.fromCallable(() -> {
                    String sql = "SELECT serial, park_code, secret FROM " + tableName +
                                 " WHERE serial = ? AND enabled = 1";
                    List<AuthInfo> result = jdbcTemplate.query(sql, (rs, rowNum) ->
                            new AuthInfo(rs.getString("serial"), rs.getString("park_code"), rs.getString("secret")),
                            serial);
                    return result.isEmpty() ? null : result.get(0);
                });
    }

    @Override
    public Mono<Boolean> validate(String serial, String secret) {
        return findBySerial(serial)
                .map(info -> info != null && info.getSecret().equals(secret));
    }
}
