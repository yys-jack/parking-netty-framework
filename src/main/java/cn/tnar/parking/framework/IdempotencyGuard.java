package cn.tnar.parking.framework;

import io.netty.channel.Channel;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 消息幂等性守卫
 * 基于滑动窗口实现同一连接内的消息去重
 */
@Slf4j
@Component
public class IdempotencyGuard {

    private static final long WINDOW_MILLIS = 10_000; // 10 秒滑动窗口
    private static final long CLEANUP_INTERVAL_MILLIS = 1_000; // 1 秒清理一次

    /**
     * Channel Attribute Key，存储已处理的消息 ID 缓存
     */
    private static final AttributeKey<ConcurrentHashMap<Integer, Long>> SEEN_MESSAGES_ATTR =
        AttributeKey.newInstance("seen_messages");

    /**
     * 全局清理时间戳（用于定期清理）
     */
    private static final AtomicLong lastCleanupTime = new AtomicLong(0);

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

        // 定期清理：每秒清理一次，而非每次调用都清理
        if (now - lastCleanupTime.get() > CLEANUP_INTERVAL_MILLIS) {
            cleanupExpired(seen, now);
            lastCleanupTime.set(now);
        }

        // 检查是否重复（只检查当前消息 ID 是否在窗口内）
        long cutoff = now - WINDOW_MILLIS;
        Long existingTs = seen.putIfAbsent(messageId, now);
        if (existingTs != null) {
            // 如果时间戳在窗口内，则是重复消息
            if (existingTs >= cutoff) {
                log.debug("[IDEMPOTENCY] Duplicate message detected: parkCode={}, messageId={}", parkCode, messageId);
                return false;
            } else {
                // 时间戳已过期，覆盖它
                seen.put(messageId, now);
            }
        }
        return true;
    }

    /**
     * 清理过期的消息记录
     */
    private void cleanupExpired(ConcurrentHashMap<Integer, Long> seen, long now) {
        long cutoff = now - WINDOW_MILLIS;
        seen.values().removeIf(ts -> ts < cutoff);
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

    /**
     * 清理 Channel 的幂等缓存（在 Channel 关闭时调用，防止内存泄漏）
     */
    public void cleanup(Channel channel) {
        channel.attr(SEEN_MESSAGES_ATTR).set(null);
    }
}
