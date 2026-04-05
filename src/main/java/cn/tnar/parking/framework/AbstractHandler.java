package cn.tnar.parking.framework;

import cn.tnar.parking.aspect.MetricsAspect;
import cn.tnar.parking.model.Respond;
import cn.tnar.parking.model.RespondProto;
import cn.tnar.parking.model.Transmission;
import cn.tnar.parking.netty.handler.Handler;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;

/**
 * 抽象消息处理器基类
 * 提供统一的超时控制、日志和异常处理框架，以及消息指标收集
 */
@Slf4j
public abstract class AbstractHandler implements Handler {

    protected final MetricsAspect metricsAspect;
    private static final int TIMEOUT_SECONDS = 10;

    public AbstractHandler(MetricsAspect metricsAspect) {
        this.metricsAspect = metricsAspect;
    }

    @Override
    public Mono<Respond> handle(Transmission transmission, String parkCode) {
        long start = System.nanoTime();

        return Mono.fromCallable(() -> doHandle(transmission, parkCode))
            .timeout(Duration.ofSeconds(TIMEOUT_SECONDS))
            .doOnSuccess(respond -> {
                long duration = System.nanoTime() - start;
                double durationSeconds = duration / 1_000_000_000.0;
                metricsAspect.recordMessage(
                    transmission.getType().name(),
                    parkCode,
                    durationSeconds,
                    true
                );
            })
            .doOnError(error -> {
                long duration = System.nanoTime() - start;
                double durationSeconds = duration / 1_000_000_000.0;
                metricsAspect.recordMessage(
                    transmission.getType().name(),
                    parkCode,
                    durationSeconds,
                    false
                );
                if (error instanceof java.util.concurrent.TimeoutException) {
                    metricsAspect.recordTimeout(transmission.getType().name(), parkCode);
                }
                log.error("[ERROR] Handler 处理超时或失败，parkCode={}, type={}",
                    parkCode, transmission.getType(), error);
            })
            .onErrorResume(e -> Mono.just(RespondProto.failure("处理超时：" + e.getMessage())))
            .subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 子类实现的业务逻辑
     */
    protected abstract Respond doHandle(Transmission transmission, String parkCode) throws Exception;
}
