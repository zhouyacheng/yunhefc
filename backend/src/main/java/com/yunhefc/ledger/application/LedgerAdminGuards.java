package com.yunhefc.ledger.application;

import com.yunhefc.common.error.BusinessException;
import com.yunhefc.common.error.ErrorCode;
import com.yunhefc.contracts.activity.ActivityReadPort;
import com.yunhefc.contracts.player.PlayerReadPort;
import com.yunhefc.ledger.domain.LedgerCategory;
import com.yunhefc.ledger.domain.LedgerRules;
import com.yunhefc.ledger.infrastructure.LedgerCategoryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 账本写操作的共享守卫：分类、球员、活动归属与通用错误。
 */
@Service
@RequiredArgsConstructor
class LedgerAdminGuards {

    private final LedgerCategoryMapper categoryMapper;
    private final PlayerReadPort playerReadPort;
    private final ActivityReadPort activityReadPort;

    LedgerCategory requireCategory(Long teamId, Long categoryId) {
        LedgerCategory category = categoryMapper.selectByIdAndTeam(categoryId, teamId);
        if (category == null) {
            throw LedgerRules.invalid("categoryId", "分类不存在或不属于本队");
        }
        return category;
    }

    LedgerCategory requireActiveCategory(Long teamId, Long categoryId) {
        LedgerCategory category = requireCategory(teamId, categoryId);
        if (!Boolean.TRUE.equals(category.getIsActive())) {
            throw LedgerRules.invalid("categoryId", "分类已停用");
        }
        return category;
    }

    void requireTeamPlayer(Long teamId, Long playerId) {
        playerReadPort.findById(playerId)
                .filter(player -> teamId.equals(player.teamId()))
                .orElseThrow(() -> LedgerRules.invalid("playerId", "归属球员不存在或不属于本队"));
    }

    void requireTeamActivity(Long teamId, Long activityId) {
        activityReadPort.findById(teamId, activityId)
                .orElseThrow(() -> LedgerRules.invalid("activityId", "关联活动不存在或不属于本队"));
    }

    BusinessException versionConflict(int currentVersion) {
        return new BusinessException(ErrorCode.VERSION_CONFLICT, "流水已被他人修改，请刷新后重试",
                Map.of("currentVersion", currentVersion));
    }

}
