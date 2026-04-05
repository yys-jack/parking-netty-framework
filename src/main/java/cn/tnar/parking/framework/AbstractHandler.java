package cn.tnar.parking.framework;

import cn.tnar.parking.aspect.MetricsAspect;
import cn.tnar.parking.model.Respond;
import cn.tnar.parking.model.Transmission;
import lombok.extern.slf4j.Slf4j;

/**
 * 抽象消息处理器基类
 * 提供统一的日志和异常处理框架，以及消息指标收集
 */
@Slf4j
public abstract class AbstractHandler implements cn.tnar.parking.netty.handler.Handler {

    protected final MetricsAspect metricsAspect;

    public AbstractHandler(MetricsAspect metricsAspect) {
        this.metricsAspect = metricsAspect;
    }

    @Override
    public final Respond handle(Transmission transmission, String parkCode) {
        long start = System.nanoTime();
        boolean success = false;
        try {
            log.debug("[HANDLER] Processing {} for parkCode={}", processedType(), parkCode);
            Respond respond = doHandle(transmission, parkCode);
            success = true;
            return respond;
        } catch (RuntimeException e) {
            log.error("[HANDLER] Error processing {}: {}", processedType(), e.getMessage());
            throw e; // 由 DispatcherHandler 统一处理
        } catch (Exception e) {
            log.error("[HANDLER] Error processing {}: {}", processedType(), e.getMessage());
            throw new RuntimeException(e);
        } finally {
            long duration = System.nanoTime() - start;
            double durationSeconds = duration / 1_000_000_000.0;
            metricsAspect.recordMessage(
                processedType().name(),
                parkCode,
                durationSeconds,
                success
            );
        }
    }

    /**
     * 子类实现的业务逻辑
     */
    protected abstract Respond doHandle(Transmission transmission, String parkCode) throws Exception;
}
