package cn.tnar.parking.netty.handler;

import cn.tnar.parking.aspect.MetricsAspect;
import cn.tnar.parking.framework.AbstractHandler;
import cn.tnar.parking.model.CarOut;
import cn.tnar.parking.model.Respond;
import cn.tnar.parking.model.RespondProto;
import cn.tnar.parking.model.Transmission;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 车辆出场处理器
 */
@Slf4j
@Component
public class CarOutHandler extends AbstractHandler {

    public CarOutHandler(MetricsAspect metricsAspect) {
        super(metricsAspect);
    }

    @Override
    public Transmission.MessageType processedType() {
        return Transmission.MessageType.TYPE_CAR_OUT;
    }

    @Override
    protected Respond doHandle(Transmission transmission, String parkCode) throws Exception {
        CarOut carOut = CarOut.parseFrom(transmission.getMessage());
        log.info("CarOutHandler 接收车辆出场，parkCode: {}, 车牌：{}, 通道：{}, 时长：{}秒",
                parkCode, carOut.getPlateNo(), carOut.getChannel(), carOut.getDuration());
        return RespondProto.success();
    }
}
