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
    private static final Map<String, ChannelId> ACTIVE_CHANNELS = new ConcurrentHashMap<>(128);
    private static final Map<ChannelId, String> ACTIVE_CHANNELS_INDEX = new ConcurrentHashMap<>(128);

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
        ChannelId id = channel.id();
        ACTIVE_CHANNELS.put(parkCode, id);
        ACTIVE_CHANNELS_INDEX.put(id, parkCode);
        log.info("add channel {}", id);
        if (connectionCallback != null) {
            connectionCallback.accept(parkCode);
        }
    }

    public static void remove(Channel channel) {
        CHANNEL_GROUP.remove(channel);
        ChannelId id = channel.id();
        // 使用原子操作移除，避免竞态条件
        String parkCode = ACTIVE_CHANNELS_INDEX.remove(id);
        if (parkCode != null) {
            // 使用原子 remove(key, value) 方法，只有值匹配时才删除
            ACTIVE_CHANNELS.remove(parkCode, id);
            if (disconnectionCallback != null) {
                disconnectionCallback.accept(parkCode);
            }
        }
        log.info("remove channel {}", id);
    }

    public static Channel getChannel(String parkCode) {
        ChannelId channelId = ACTIVE_CHANNELS.get(parkCode);
        if (Objects.nonNull(channelId)) {
            return CHANNEL_GROUP.find(channelId);
        }
        throw new RuntimeException("Channel not found via parkCode");
    }

    /**
     * 获取活跃连接数
     */
    public static int getActiveCount() {
        return ACTIVE_CHANNELS.size();
    }

    /**
     * 清空所有连接状态（仅用于测试）
     */
    public static void clearAll() {
        ACTIVE_CHANNELS.clear();
        ACTIVE_CHANNELS_INDEX.clear();
        CHANNEL_GROUP.close().syncUninterruptibly();
    }
}
