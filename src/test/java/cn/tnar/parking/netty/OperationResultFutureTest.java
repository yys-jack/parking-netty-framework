package cn.tnar.parking.netty;

import cn.tnar.parking.model.ExistedFutureException;
import cn.tnar.parking.model.Respond;
import cn.tnar.parking.model.RespondProto;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * OperationResultFuture 测试
 */
class OperationResultFutureTest {

    @AfterEach
    void tearDown() {
        // 清理可能残留的 future
        OperationResultFuture.remove("test-key");
    }

    @Test
    void testCreateAndGet() {
        String key = "test-key";

        // 创建 future
        OperationResultFuture future = OperationResultFuture.create(key);
        assertNotNull(future);

        // 验证能获取到
        OperationResultFuture retrieved = OperationResultFuture.get(key);
        assertSame(future, retrieved);
    }

    @Test
    void testCreateDuplicate() {
        String key = "test-duplicate-key";

        // 第一次创建成功
        OperationResultFuture.create(key);

        // 第二次创建应该抛出异常
        assertThrows(ExistedFutureException.class, () -> {
            OperationResultFuture.create(key);
        });
    }

    @Test
    void testRemove() {
        String key = "test-remove-key";

        // 创建并移除
        OperationResultFuture.create(key);
        OperationResultFuture.remove(key);

        // 验证移除后获取不到
        OperationResultFuture retrieved = OperationResultFuture.get(key);
        assertNull(retrieved);
    }

    @Test
    void testSuccessNotification() {
        String key = "test-success-key";

        OperationResultFuture future = OperationResultFuture.create(key);

        // 设置成功结果
        Respond respond = RespondProto.success();
        future.setSuccess(respond);

        // 验证能获取到结果
        assertTrue(future.isSuccess());
        assertEquals(respond, future.getNow());

        OperationResultFuture.remove(key);
    }
}
