package cn.tnar.parking.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 认证配置属性
 */
@Component
@ConfigurationProperties(prefix = "auth")
public class AuthProperties {
    private List<AuthEntry> entries = new ArrayList<>();

    public List<AuthEntry> getEntries() {
        return entries;
    }

    public void setEntries(List<AuthEntry> entries) {
        this.entries = entries;
    }

    public static class AuthEntry {
        private String serial;
        private String parkCode;
        private String secret;

        public String getSerial() {
            return serial;
        }

        public void setSerial(String serial) {
            this.serial = serial;
        }

        public String getParkCode() {
            return parkCode;
        }

        public void setParkCode(String parkCode) {
            this.parkCode = parkCode;
        }

        public String getSecret() {
            return secret;
        }

        public void setSecret(String secret) {
            this.secret = secret;
        }
    }
}
