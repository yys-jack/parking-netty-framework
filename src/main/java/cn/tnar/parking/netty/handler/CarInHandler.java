package cn.tnar.parking.netty.handler;

import cn.tnar.parking.aspect.MetricsAspect;
import cn.tnar.parking.framework.AbstractHandler;
import cn.tnar.parking.model.CarIn;
import cn.tnar.parking.model.Respond;
import cn.tnar.parking.model.RespondProto;
import cn.tnar.parking.model.Transmission;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 车辆入场处理器
 */
@Slf4j
@Component
public class CarInHandler extends AbstractHandler {

    public CarInHandler(MetricsAspect metricsAspect) {
        super(metricsAspect);
    }

    @Override
    public Transmission.MessageType processedType() {
        return Transmission.MessageType.TYPE_CAR_IN;
    }

    @Override
    protected Respond doHandle(Transmission transmission, String parkCode) throws Exception {
        CarIn carIn = CarIn.parseFrom(transmission.getMessage());
        log.info("CarInHandler 接收车辆入场，parkCode: {}, 车牌：{}, 通道：{}, 时间：{}",
                parkCode, carIn.getPlateNo(), carIn.getChannel(), carIn.getInTime());
        return RespondProto.success();
    }
}
