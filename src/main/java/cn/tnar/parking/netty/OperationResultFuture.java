package cn.tnar.parking.netty;

import cn.tnar.parking.model.ExistedFutureException;
import cn.tnar.parking.model.Respond;
import io.netty.util.concurrent.DefaultPromise;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 操作结果 Future - 用于异步等待响应
 */
public class OperationResultFuture extends DefaultPromise<Respond> {
    private static final Map<String, OperationResultFuture> FUTURE_MAP = new ConcurrentHashMap<>(1024);

    private OperationResultFuture() {
    }

    public static OperationResultFuture create(String key) {
        OperationResultFuture future = new OperationResultFuture();
        if (FUTURE_MAP.containsKey(key)) {
            throw new ExistedFutureException("existed future");
        }
        FUTURE_MAP.put(key, future);
        return future;
    }

    public static void remove(String key) {
        FUTURE_MAP.remove(key);
    }

    public static OperationResultFuture get(String key) {
        return FUTURE_MAP.get(key);
    }

}
