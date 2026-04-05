package cn.tnar.parking.repository.auth;

import cn.tnar.parking.config.AuthProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 内存认证信息仓库实现
 */
@Component
@ConditionalOnProperty(prefix = "auth.storage", name = "type", havingValue = "in-memory", matchIfMissing = true)
public class InMemoryAuthRepository implements ReactiveAuthRepository {

    private final Map<String, AuthInfo> authInfoMap;

    public InMemoryAuthRepository(AuthProperties properties) {
        this.authInfoMap = properties.getEntries().stream()
                .map(entry -> AuthInfo.fromEntry(entry.getSerial(), entry.getParkCode(), entry.getSecret()))
                .collect(Collectors.toMap(AuthInfo::getSerial, Function.identity()));
    }

    @Override
    public Mono<AuthInfo> findBySerial(String serial) {
        return Mono.justOrEmpty(authInfoMap.get(serial));
    }

    @Override
    public Mono<Boolean> validate(String serial, String secret) {
        return findBySerial(serial)
                .map(info -> info.getSecret().equals(secret))
                .defaultIfEmpty(false);
    }
}
