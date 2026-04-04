package cn.tnar.parking.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RespondProto 测试
 */
class RespondProtoTest {

    @Test
    void testSuccess() {
        Respond respond = RespondProto.success();
        assertEquals(Respond.RespondCode.CODE_SUCCESS, respond.getCode());
    }

    @Test
    void testFailure() {
        Respond respond = RespondProto.failure();
        assertEquals(Respond.RespondCode.CODE_FAILURE, respond.getCode());
    }

    @Test
    void testTimeout() {
        Respond respond = RespondProto.timeout();
        assertEquals(Respond.RespondCode.CODE_TIMEOUT, respond.getCode());
    }

    @Test
    void testDuplicate() {
        Respond respond = RespondProto.duplicate();
        assertEquals(Respond.RespondCode.CODE_DUPLICATE, respond.getCode());
    }

    @Test
    void testMismatch() {
        Respond respond = RespondProto.mismatch();
        assertEquals(Respond.RespondCode.CODE_MISMATCH, respond.getCode());
    }
}
