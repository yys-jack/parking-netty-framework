package cn.tnar.parking.repository.auth;

import reactor.core.publisher.Mono;

/**
 * 认证信息仓库接口（响应式）
 */
public interface ReactiveAuthRepository {
    /**
     * 根据序列号获取认证信息
     */
    Mono<AuthInfo> findBySerial(String serial);

    /**
     * 验证认证信息
     */
    Mono<Boolean> validate(String serial, String secret);
}
