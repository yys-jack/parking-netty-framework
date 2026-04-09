package cn.tnar.parking.framework;

import io.netty.channel.Channel;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 消息幂等性守卫
 * 基于滑动窗口实现同一连接内的消息去重
 */
@Slf4j
@Component
public class IdempotencyGuard {

    private static final long WINDOW_MILLIS = 10_000; // 10 秒滑动窗口

    /**
     * Channel Attribute Key，存储已处理的消息 ID 缓存
     */
    private static final AttributeKey<ConcurrentHashMap<Integer, Long>> SEEN_MESSAGES_ATTR =
        AttributeKey.newInstance("seen_messages");

    /**
     * 尝试获取消息处理权
     *
     * @param channel   TCP 连接
     * @param parkCode  场端编码（用于日志和监控）
     * @param messageId 消息 ID
     * @return true=新消息，允许处理；false=重复消息，应拒绝
     */
    public boolean tryAcquire(Channel channel, String parkCode, int messageId) {
        ConcurrentHashMap<Integer, Long> seen = getOrCreateCache(channel);

        long now = System.currentTimeMillis();
        long cutoff = now - WINDOW_MILLIS;

        // 滑动窗口清理：移除超过 10 秒的记录
        seen.values().removeIf(ts -> ts < cutoff);

        // 尝试放入新记录，返回 null 表示第一次见到此 ID
        Long existingTs = seen.putIfAbsent(messageId, now);
        if (existingTs != null) {
            // 重复消息
            log.debug("[IDEMPOTENCY] Duplicate message detected: parkCode={}, messageId={}", parkCode, messageId);
            return false;
        }
        return true;
    }

    /**
     * 获取或创建 Channel 的幂等缓存
     */
    private ConcurrentHashMap<Integer, Long> getOrCreateCache(Channel channel) {
        ConcurrentHashMap<Integer, Long> seen = channel.attr(SEEN_MESSAGES_ATTR).get();
        if (seen == null) {
            seen = new ConcurrentHashMap<>();
            channel.attr(SEEN_MESSAGES_ATTR).set(seen);
        }
        return seen;
    }

    /**
     * 获取缓存中的消息数量（用于监控）
     */
    public int getSeenCount(Channel channel) {
        ConcurrentHashMap<Integer, Long> seen = channel.attr(SEEN_MESSAGES_ATTR).get();
        return seen != null ? seen.size() : 0;
    }
}
