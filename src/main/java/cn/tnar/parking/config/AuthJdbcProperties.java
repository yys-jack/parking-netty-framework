package cn.tnar.parking.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "auth.jdbc")
public class AuthJdbcProperties {

    private String url = "jdbc:mysql://localhost:3306/parking?useSSL=false&serverTimezone=UTC&characterEncoding=utf8";
    private String username = "root";
    private String password = "";
    private String table = "auth_info";
    private String driverClassName = "com.mysql.cj.jdbc.Driver";

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getTable() { return table; }
    public void setTable(String table) { this.table = table; }
    public String getDriverClassName() { return driverClassName; }
    public void setDriverClassName(String driverClassName) { this.driverClassName = driverClassName; }
}
