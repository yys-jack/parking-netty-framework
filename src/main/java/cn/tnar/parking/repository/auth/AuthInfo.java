package cn.tnar.parking.repository.auth;

/**
 * 认证信息实体
 */
public class AuthInfo {
    private final String serial;
    private final String parkCode;
    private final String secret;

    public AuthInfo(String serial, String parkCode, String secret) {
        this.serial = serial;
        this.parkCode = parkCode;
        this.secret = secret;
    }

    public static AuthInfo fromEntry(String serial, String parkCode, String secret) {
        return new AuthInfo(serial, parkCode, secret);
    }

    public String getSerial() {
        return serial;
    }

    public String getParkCode() {
        return parkCode;
    }

    public String getSecret() {
        return secret;
    }
}
