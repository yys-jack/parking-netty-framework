package cn.tnar.parking.framework;

import cn.tnar.parking.model.Respond;
import cn.tnar.parking.model.Transmission;
import lombok.extern.slf4j.Slf4j;

/**
 * 抽象消息处理器基类
 * 提供统一的日志和异常处理框架
 */
@Slf4j
public abstract class AbstractHandler implements cn.tnar.parking.netty.handler.Handler {

    @Override
    public final Respond handle(Transmission transmission, String parkCode) {
        try {
            log.debug("[HANDLER] Processing {} for parkCode={}", processedType(), parkCode);
            return doHandle(transmission, parkCode);
        } catch (RuntimeException e) {
            log.error("[HANDLER] Error processing {}: {}", processedType(), e.getMessage());
            throw e; // 由 DispatcherHandler 统一处理
        } catch (Exception e) {
            log.error("[HANDLER] Error processing {}: {}", processedType(), e.getMessage());
            throw new RuntimeException(e);
        }
    }

    /**
     * 子类实现的业务逻辑
     */
    protected abstract Respond doHandle(Transmission transmission, String parkCode) throws Exception;
}
