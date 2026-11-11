package com.yunhefc.lineup.domain;

import com.yunhefc.common.error.BusinessException;
import com.yunhefc.common.error.ErrorCode;
import com.yunhefc.contracts.match.MatchSnapshot;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 阵容领域规则：主客方判定、可编辑状态、槽位与球员校验。
 * 对外比赛（OFFICIAL/FRIENDLY）中非我方一侧仅记录阵型与空槽位。
 */
public final class LineupRules {

    public static final int SIDE_HOME = 0;
    public static final int SIDE_AWAY = 1;

    private LineupRules() {
    }

    public record Starter(String slotKey, Long playerId) {
    }

    public static int requireSide(Integer side) {
        if (side == null || (side != SIDE_HOME && side != SIDE_AWAY)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    "side 必须为 0（主队）或 1（客队）");
        }
        return side;
    }

    public static boolean opponentSide(MatchSnapshot match, int side) {
        if (match.internalOrTraining()) {
            return false;
        }
        return side != match.ourEventTeam();
    }

    /**
     * 非终态且未延期可编辑；终态（已结束/已取消）与已延期禁用。
     */
    public static boolean editable(MatchSnapshot match) {
        return match.status() != null && !match.finished() && !match.cancelledOrPostponed();
    }

    /**
     * 首发槽位必须与所选阵型完全一致；我方每槽必填，对手方只能为空槽，且同一球员不可重复。
     */
    public static void validateSelection(Collection<String> templateSlotKeys,
                                         List<Starter> starters,
                                         List<Long> bench,
                                         boolean opponent) {
        Set<String> expected = new HashSet<>(templateSlotKeys);
        if (starters.size() != expected.size()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "首发槽位与阵型不一致");
        }
        Set<String> seenSlots = new HashSet<>();
        for (Starter starter : starters) {
            if (!expected.contains(starter.slotKey()) || !seenSlots.add(starter.slotKey())) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, "首发槽位与阵型不一致");
            }
        }

        if (opponent) {
            boolean hasPlayer = starters.stream().anyMatch(starter -> starter.playerId() != null);
            if (hasPlayer || !bench.isEmpty()) {
                throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                        "对手方阵容只能记录阵型与空槽位");
            }
            return;
        }

        if (starters.stream().anyMatch(starter -> starter.playerId() == null)) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "首发必须排满 " + expected.size() + " 人，每个位置都需选择球员");
        }

        Set<Long> players = new HashSet<>();
        starters.forEach(starter -> {
            if (!players.add(starter.playerId())) {
                throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                        "同一球员不能同时出现在首发和替补");
            }
        });
        for (Long playerId : bench) {
            if (playerId == null || !players.add(playerId)) {
                throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                        "同一球员不能同时出现在首发和替补");
            }
        }
    }
}
