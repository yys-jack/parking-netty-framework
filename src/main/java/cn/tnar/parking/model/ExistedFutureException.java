package cn.tnar.parking.model;

/**
 * 已存在的 Future 异常
 */
public class ExistedFutureException extends RuntimeException {
    public ExistedFutureException(String message) {
        super(message);
    }
}
