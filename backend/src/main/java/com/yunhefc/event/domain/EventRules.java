package com.yunhefc.event.domain;

import com.yunhefc.common.error.BusinessException;
import com.yunhefc.common.error.ErrorCode;

/**
 * 事件字段矩阵：结构错误 40001，跨字段业务规则 42201（04 §8.1）。
 */
public final class EventRules {

    private EventRules() {
    }

    public record EventFields(
            EventType type,
            EventTeam team,
            Long playerId,
            Long assistPlayerId,
            Long inPlayerId,
            Long outPlayerId,
            Integer minute,
            EventHalf half,
            boolean penalty) {
    }

    public static void validate(EventFields fields) {
        if (fields.type() == null || fields.team() == null || fields.half() == null
                || fields.minute() == null || fields.minute() < 1 || fields.minute() > 120) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    "type/team/half/minute 必填且 minute 必须为 1–120");
        }
        if (fields.type() == EventType.SUBSTITUTION) {
            require(fields.playerId() == null, "换人事件不能填写球员");
            require(fields.inPlayerId() != null && fields.outPlayerId() != null,
                    "换人事件必须填写换上和换下球员");
            require(!fields.inPlayerId().equals(fields.outPlayerId()),
                    "换上和换下球员不能相同");
            require(fields.assistPlayerId() == null && !fields.penalty(),
                    "换人事件不能填写助攻或点球");
            return;
        }
        require(fields.playerId() != null, "非换人事件必须填写球员");
        require(fields.inPlayerId() == null && fields.outPlayerId() == null,
                "只有换人事件可以填写换上/换下球员");
        if (fields.type() == EventType.GOAL) {
            require(fields.assistPlayerId() == null
                            || !fields.assistPlayerId().equals(fields.playerId()),
                    "助攻球员不能是进球球员本人");
            return;
        }
        require(fields.assistPlayerId() == null && !fields.penalty(),
                "助攻与点球只能用于进球事件");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, message);
        }
    }
}
