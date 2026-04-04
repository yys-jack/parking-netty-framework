package cn.tnar.parking.repository.auth;

import java.util.Optional;

/**
 * 认证信息仓库接口
 */
public interface AuthRepository {
    /**
     * 根据序列号获取认证信息
     */
    Optional<AuthInfo> findBySerial(String serial);

    /**
     * 验证认证信息
     */
    boolean validate(String serial, String secret);
}
