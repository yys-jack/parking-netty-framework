package cn.tnar.parking.repository.auth;

import cn.tnar.parking.config.AuthProperties;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 内存认证信息仓库实现
 * 生产环境可替换为 Redis 或数据库实现
 */
@Component
public class InMemoryAuthRepository implements AuthRepository {
    private final List<AuthInfo> authInfos;

    public InMemoryAuthRepository(AuthProperties properties) {
        this.authInfos = properties.getEntries().stream()
                .map(entry -> AuthInfo.fromEntry(entry.getSerial(), entry.getParkCode(), entry.getSecret()))
                .collect(Collectors.toList());
    }

    @Override
    public Optional<AuthInfo> findBySerial(String serial) {
        return authInfos.stream()
                .filter(info -> info.getSerial().equals(serial))
                .findFirst();
    }

    @Override
    public boolean validate(String serial, String secret) {
        return findBySerial(serial)
                .map(info -> info.getSecret().equals(secret))
                .orElse(false);
    }
}
