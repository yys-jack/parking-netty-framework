package cn.tnar.parking.netty.handler;

import cn.tnar.parking.aspect.MetricsAspect;
import cn.tnar.parking.framework.AbstractHandler;
import cn.tnar.parking.model.Respond;
import cn.tnar.parking.model.RespondProto;
import cn.tnar.parking.model.Space;
import cn.tnar.parking.model.Transmission;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 车位上报处理器
 */
@Slf4j
@Component
public class SpaceHandler extends AbstractHandler {

    public SpaceHandler(MetricsAspect metricsAspect) {
        super(metricsAspect);
    }

    @Override
    public Transmission.MessageType processedType() {
        return Transmission.MessageType.TYPE_SPACE;
    }

    @Override
    protected Respond doHandle(Transmission transmission, String parkCode) throws Exception {
        Space space = Space.parseFrom(transmission.getMessage());
        log.info("SpaceHandler 接收车位上报，parkCode: {}, 总车位：{}, 剩余车位：{}",
                parkCode, space.getTotal(), space.getRemained());
        return RespondProto.success();
    }
}
