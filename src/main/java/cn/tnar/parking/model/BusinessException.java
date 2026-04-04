package cn.tnar.parking.model;

import cn.tnar.parking.model.Respond.RespondCode;

/**
 * 业务异常
 */
public class BusinessException extends RuntimeException {
    private final RespondCode code;

    public BusinessException(String message) {
        super(message);
        this.code = RespondCode.CODE_FAILURE;
    }

    public BusinessException(String message, RespondCode code) {
        super(message);
        this.code = code;
    }

    public RespondCode getCode() {
        return code;
    }
}
