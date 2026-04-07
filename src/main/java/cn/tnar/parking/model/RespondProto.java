package cn.tnar.parking.model;

import com.google.protobuf.ByteString;

/**
 * 响应原型工厂
 */
public final class RespondProto {

    private RespondProto() {
    }

    public static Respond success() {
        return Respond.newBuilder()
                .setCode(Respond.RespondCode.CODE_SUCCESS)
                .build();
    }

    public static Respond failure() {
        return Respond.newBuilder()
                .setCode(Respond.RespondCode.CODE_FAILURE)
                .build();
    }

    public static Respond failure(String message) {
        return Respond.newBuilder()
                .setCode(Respond.RespondCode.CODE_FAILURE)
                .setMessage(ByteString.copyFromUtf8(message))
                .build();
    }

    public static Respond timeout() {
        return Respond.newBuilder()
                .setCode(Respond.RespondCode.CODE_TIMEOUT)
                .build();
    }

    public static Respond duplicate() {
        return Respond.newBuilder()
                .setCode(Respond.RespondCode.CODE_DUPLICATE)
                .build();
    }

    public static Respond mismatch() {
        return Respond.newBuilder()
                .setCode(Respond.RespondCode.CODE_MISMATCH)
                .build();
    }

    public static Respond busy() {
        return Respond.newBuilder()
                .setCode(Respond.RespondCode.CODE_BUSY)
                .setMessage(ByteString.copyFromUtf8("服务器繁忙，请稍后重试"))
                .build();
    }
}
