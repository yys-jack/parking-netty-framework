package cn.tnar.parking.netty.handler;

import cn.tnar.parking.aspect.MetricsAspect;
import cn.tnar.parking.framework.AbstractHandler;
import cn.tnar.parking.model.Respond;
import cn.tnar.parking.model.RespondProto;
import cn.tnar.parking.model.Space;
import cn.tnar.parking.model.Transmission;
import cn.tnar.parking.repository.space.SpaceSnapshotAggregator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 车位上报处理器
 */
@Slf4j
@Component
public class SpaceHandler extends AbstractHandler {

    private final SpaceSnapshotAggregator snapshotAggregator;

    public SpaceHandler(MetricsAspect metricsAspect, SpaceSnapshotAggregator snapshotAggregator) {
        super(metricsAspect);
        this.snapshotAggregator = snapshotAggregator;
    }

    @Override
    public Transmission.MessageType processedType() {
        return Transmission.MessageType.TYPE_SPACE;
    }

    @Override
    protected Respond doHandle(Transmission transmission, String parkCode) throws Exception {
        Space space = Space.parseFrom(transmission.getMessage());

        // 添加到聚合器，由定时任务批量写入数据库
        snapshotAggregator.addSnapshot(
            parkCode,
            space.getTotal(),
            space.getRemained(),
            System.currentTimeMillis()
        );

        log.info("SpaceHandler 接收车位上报，parkCode: {}, 总车位：{}, 剩余车位：{}",
                parkCode, space.getTotal(), space.getRemained());

        return RespondProto.success();
    }
}
