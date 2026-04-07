package cn.tnar.parking.netty;

import io.netty.channel.Channel;
import io.netty.channel.ChannelId;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.util.AttributeKey;
import io.netty.util.concurrent.DefaultEventExecutor;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * 渠道维护器 - 管理 Netty Channel 连接
 */
@Slf4j
public final class ChannelMaintainer {
    public static final AttributeKey<String> PARK_CODE_ATTR = AttributeKey.valueOf("parkCode");
    private static final DefaultChannelGroup CHANNEL_GROUP = new DefaultChannelGroup("connection-maintainer", new DefaultEventExecutor());

    /**
     * 使用 Channel 对象本身作为 key（避免 EmbeddedChannelId 重复问题）
     */
    private static final Map<Channel, String> CHANNEL_PARK_MAP = new ConcurrentHashMap<>(128);

    // 指标回调
    private static Consumer<String> connectionCallback;
    private static Consumer<String> disconnectionCallback;

    public static void setConnectionCallback(Consumer<String> callback) {
        connectionCallback = callback;
    }

    public static void setDisconnectionCallback(Consumer<String> callback) {
        disconnectionCallback = callback;
    }

    public static void add(String parkCode, Channel channel) {
        CHANNEL_GROUP.add(channel);
        CHANNEL_PARK_MAP.put(channel, parkCode);
        log.info("add channel {}, parkCode={}", channel.id(), parkCode);

        // 添加关闭监听，确保 channel 关闭时自动清理，避免内存泄漏（修复 C4）
        channel.closeFuture().addListener(future -> {
            CHANNEL_PARK_MAP.remove(channel);
            log.debug("Auto-removed closed channel from map: {}", channel.id());
        });

        if (connectionCallback != null) {
            connectionCallback.accept(parkCode);
        }
    }

    public static void remove(Channel channel) {
        CHANNEL_GROUP.remove(channel);
        String parkCode = CHANNEL_PARK_MAP.remove(channel);
        if (parkCode != null && disconnectionCallback != null) {
            disconnectionCallback.accept(parkCode);
        }
        log.info("remove channel {}, parkCode={}", channel.id(), parkCode);
    }

    public static Channel getChannel(String parkCode) {
        // 遍历查找（低频操作，通常只在需要主动推送时使用）
        for (Map.Entry<Channel, String> entry : CHANNEL_PARK_MAP.entrySet()) {
            if (Objects.equals(entry.getValue(), parkCode)) {
                return entry.getKey();
            }
        }
        // 修复 I5: 异常消息包含 parkCode 参数
        throw new RuntimeException("Channel not found for parkCode: " + parkCode);
    }

    /**
     * 获取活跃连接数
     */
    public static int getActiveCount() {
        return CHANNEL_PARK_MAP.size();
    }

    /**
     * 清空所有连接状态（仅用于测试）
     */
    public static void clearAll() {
        CHANNEL_PARK_MAP.clear();
        CHANNEL_GROUP.close().syncUninterruptibly();
    }
}
