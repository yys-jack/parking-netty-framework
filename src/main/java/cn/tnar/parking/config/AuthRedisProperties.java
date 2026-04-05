package cn.tnar.parking.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "auth.redis")
public class AuthRedisProperties {

    private String host = "localhost";
    private int port = 6379;
    private String password = "";
    private int database = 0;
    private String keyPrefix = "parking:auth:";

    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }
    public int getPort() { return port; }
    public void setPort(int port) { this.port = port; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public int getDatabase() { return database; }
    public void setDatabase(int database) { this.database = database; }
    public String getKeyPrefix() { return keyPrefix; }
    public void setKeyPrefix(String keyPrefix) { this.keyPrefix = keyPrefix; }
}
