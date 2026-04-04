package cn.tnar.parking.netty;

import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ChannelMaintainer 测试
 */
class ChannelMaintainerTest {

    private EmbeddedChannel testChannel;

    @BeforeEach
    void setUp() {
        testChannel = new EmbeddedChannel();
    }

    @AfterEach
    void tearDown() {
        testChannel.close();
    }

    @Test
    void testAddAndGetChannel() {
        String parkCode = "TEST001";

        // 添加 channel
        ChannelMaintainer.add(parkCode, testChannel);

        // 验证能获取到
        io.netty.channel.Channel channel = ChannelMaintainer.getChannel(parkCode);
        assertNotNull(channel);
        assertEquals(testChannel, channel);
    }

    @Test
    void testRemoveChannel() {
        String parkCode = "TEST002";

        // 添加并移除
        ChannelMaintainer.add(parkCode, testChannel);
        ChannelMaintainer.remove(testChannel);

        // 验证移除后获取不到（会抛异常）
        assertThrows(RuntimeException.class, () -> {
            ChannelMaintainer.getChannel(parkCode);
        });
    }

    @Test
    void testParkedCodeAttribute() {
        assertNotNull(ChannelMaintainer.PARK_CODE_ATTR);
        assertEquals("parkCode", ChannelMaintainer.PARK_CODE_ATTR.name());
    }
}
