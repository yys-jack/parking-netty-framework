package cn.tnar.parking.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Netty 配置属性
 */
@Component
public class
NettyProperties {
    @Value("${netty.tcp.port:8889}")
    private int port = 8889;

    @Value("${netty.tcp.authTimeout:10}")
    private int authTimeout = 10;

    @Value("${netty.tcp.heartbeatInterval:90}")
    private int heartbeatInterval = 90;

    @Value("${netty.tcp.backlog:128}")
    private int backlog = 128;

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public int getAuthTimeout() {
        return authTimeout;
    }

    public void setAuthTimeout(int authTimeout) {
        this.authTimeout = authTimeout;
    }

    public int getHeartbeatInterval() {
        return heartbeatInterval;
    }

    public void setHeartbeatInterval(int heartbeatInterval) {
        this.heartbeatInterval = heartbeatInterval;
    }

    public int getBacklog() {
        return backlog;
    }

    public void setBacklog(int backlog) {
        this.backlog = backlog;
    }
}
