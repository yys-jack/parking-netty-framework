package cn.tnar.parking.framework;

import io.netty.channel.Channel;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * IdempotencyGuard 单元测试
 */
class IdempotencyGuardTest {

    private IdempotencyGuard idempotencyGuard;

    @Mock
    private Channel mockChannel;

    @Mock
    private Attribute<ConcurrentHashMap<Integer, Long>> mockAttribute;

    private ConcurrentHashMap<Integer, Long> testCache;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        idempotencyGuard = new IdempotencyGuard();
        testCache = new ConcurrentHashMap<>();

        // 模拟 Channel Attribute
        when(mockChannel.attr(any(AttributeKey.class))).thenReturn(mockAttribute);
        when(mockAttribute.get()).thenReturn(testCache);
        doAnswer(invocation -> {
            testCache.put(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(mockAttribute).set(any());
    }

    @Test
    void firstMessage_shouldPass() {
        // Arrange
        String parkCode = "PARK001";
        int messageId = 100;

        // Act
        boolean result = idempotencyGuard.tryAcquire(mockChannel, parkCode, messageId);

        // Assert
        assertTrue(result, "第一次消息应该通过");
        assertEquals(1, testCache.size());
    }

    @Test
    void duplicateMessage_withinWindow_shouldFail() throws InterruptedException {
        // Arrange
        String parkCode = "PARK001";
        int messageId = 100;

        // Act - 第一次发送
        boolean firstResult = idempotencyGuard.tryAcquire(mockChannel, parkCode, messageId);

        // Act - 重复发送（在窗口内）
        boolean secondResult = idempotencyGuard.tryAcquire(mockChannel, parkCode, messageId);

        // Assert
        assertTrue(firstResult, "第一次消息应该通过");
        assertFalse(secondResult, "重复消息应该被拒绝");
    }

    @Test
    void duplicateMessage_afterWindow_shouldPass() throws InterruptedException {
        // Arrange
        String parkCode = "PARK001";
        int messageId = 100;

        // 手动添加一条"过期"记录（15 秒前）
        long oldTimestamp = System.currentTimeMillis() - 15_000;
        testCache.put(messageId, oldTimestamp);

        // Act - 超过窗口后重新发送相同 ID
        boolean result = idempotencyGuard.tryAcquire(mockChannel, parkCode, messageId);

        // Assert
        assertTrue(result, "窗口外的重复消息应该允许重新发送");
        assertEquals(1, testCache.size(), "过期记录应被清理，只保留新记录");
    }

    @Test
    void differentChannels_shouldHaveIndependentCache() {
        // Arrange
        Channel mockChannel2 = mock(Channel.class);
        Attribute<ConcurrentHashMap<Integer, Long>> mockAttribute2 = mock(Attribute.class);
        ConcurrentHashMap<Integer, Long> testCache2 = new ConcurrentHashMap<>();

        when(mockChannel2.attr(any(AttributeKey.class))).thenReturn(mockAttribute2);
        when(mockAttribute2.get()).thenReturn(testCache2);

        String parkCode = "PARK001";
        int messageId = 100;

        // Act - Channel 1 发送
        idempotencyGuard.tryAcquire(mockChannel, parkCode, messageId);

        // Act - Channel 2 发送相同 ID
        boolean result2 = idempotencyGuard.tryAcquire(mockChannel2, parkCode, messageId);

        // Assert
        assertTrue(result2, "不同连接的相同 ID 应该独立处理");
    }

    @Test
    void getSeenCount_shouldReturnCacheSize() {
        // Arrange
        testCache.put(1, System.currentTimeMillis());
        testCache.put(2, System.currentTimeMillis());

        // Act
        int count = idempotencyGuard.getSeenCount(mockChannel);

        // Assert
        assertEquals(2, count);
    }

    @Test
    void concurrentAccess_shouldBeThreadSafe() throws InterruptedException {
        // Arrange
        String parkCode = "PARK001";
        int messageId = 100;
        int threadCount = 10;
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        // 使用真实的 ConcurrentHashMap 而非 Mock，因为 Mock 不是线程安全的
        ConcurrentHashMap<Integer, Long> realCache = new ConcurrentHashMap<>();
        Channel realChannel = new io.netty.channel.embedded.EmbeddedChannel();
        realChannel.attr(IdempotencyGuard.SEEN_MESSAGES_ATTR).set(realCache);

        // Act - 多线程并发调用 tryAcquire
        for (int i = 0; i < threadCount; i++) {
            new Thread(() -> {
                boolean result = idempotencyGuard.tryAcquire(realChannel, parkCode, messageId);
                if (result) {
                    successCount.incrementAndGet();
                }
                latch.countDown();
            }).start();
        }

        // Wait for all threads to complete
        latch.await();

        // Assert - 只有一个线程应该成功
        assertEquals(1, successCount.get(), "只有第一个线程应该成功");
        assertEquals(1, realCache.size(), "缓存中只应有一条记录");

        // Cleanup
        realChannel.close();
    }

    @Test
    void windowBoundary_exactTimeout_shouldPass() {
        // Arrange
        String parkCode = "PARK001";
        int messageId = 100;

        // 手动添加一条恰好在窗口边界的记录（10 秒前）
        // 注意：由于实现使用 >= 判断，10 秒整的消息仍在窗口内
        // 所以这个测试预期是：10 秒整的消息被视为重复
        long exactBoundaryTimestamp = System.currentTimeMillis() - 10_000;
        testCache.put(messageId, exactBoundaryTimestamp);

        // Act - 恰好在边界上的消息被视为重复
        boolean result = idempotencyGuard.tryAcquire(mockChannel, parkCode, messageId);

        // Assert - 10 秒整的消息仍在窗口内，应被拒绝
        assertFalse(result, "恰好 10 秒的消息仍在窗口内，应被拒绝");
    }

    @Test
    void windowBoundary_beyondTimeout_shouldPass() {
        // Arrange
        String parkCode = "PARK001";
        int messageId = 100;

        // 手动添加一条超出窗口边界的记录（10.1 秒前）
        long beyondBoundaryTimestamp = System.currentTimeMillis() - 10_100;
        testCache.put(messageId, beyondBoundaryTimestamp);

        // Act - 超出边界的消息应该允许通过
        boolean result = idempotencyGuard.tryAcquire(mockChannel, parkCode, messageId);

        // Assert
        assertTrue(result, "超出窗口边界的消息应该允许重新发送");
    }

    @Test
    void windowBoundary_justInside_shouldFail() {
        // Arrange
        String parkCode = "PARK001";
        int messageId = 100;

        // 手动添加一条在窗口内但接近边界的记录（9.9 秒前）
        long justInsideTimestamp = System.currentTimeMillis() - 9_900;
        testCache.put(messageId, justInsideTimestamp);

        // Act - 窗口内的消息应该被拒绝
        boolean result = idempotencyGuard.tryAcquire(mockChannel, parkCode, messageId);

        // Assert
        assertFalse(result, "窗口内的重复消息应该被拒绝");
    }
}
