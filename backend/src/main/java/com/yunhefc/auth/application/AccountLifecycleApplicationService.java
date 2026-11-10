package com.yunhefc.auth.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yunhefc.auth.domain.AppUser;
import com.yunhefc.auth.domain.UserStatus;
import com.yunhefc.auth.infrastructure.AppUserMapper;
import com.yunhefc.audit.application.AuditRecorder;
import com.yunhefc.common.error.BusinessException;
import com.yunhefc.common.error.ErrorCode;
import com.yunhefc.common.security.CurrentUser;
import com.yunhefc.contracts.messaging.EventTypes;
import com.yunhefc.contracts.player.PlayerReadPort;
import com.yunhefc.contracts.player.PlayerSnapshot;
import com.yunhefc.contracts.security.AuthPrincipal;
import com.yunhefc.infra.outbox.OutboxService;
import com.yunhefc.player.application.PlayerAdminApplicationService;
import com.yunhefc.player.dto.PlayerDtos.PlayerAdminView;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Objects;

/**
 * 账号生命周期编排（P-05 例外）：审核、转正、注销在 auth 应用服务内同步编排
 * 账号、球员、角色与报名；其他业务域不得复制该事务边界。
 */
@Service
@RequiredArgsConstructor
public class AccountLifecycleApplicationService {

    private final AppUserMapper userMapper;
    private final PlayerReadPort playerReadPort;
    private final PlayerAdminApplicationService playerAdminService;
    private final AuditRecorder auditRecorder;
    private final OutboxService outboxService;

    /**
     * 仅 PENDING 可审核；ACTIVE 幂等返回，不重复写审计与通知；DISABLED 不可恢复。
     */
    @Transactional
    public PlayerAdminView approve(Long playerId) {
        AuthPrincipal actor = CurrentUser.principal();
        PlayerSnapshot player = playerReadPort.findById(playerId)
                .filter(snapshot -> Objects.equals(snapshot.teamId(), actor.teamId()))
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "球员档案不存在"));
        // 行锁串行化并发审核，保证审计与通知只写一次
        AppUser user = userMapper.selectOne(new LambdaQueryWrapper<AppUser>()
                .eq(AppUser::getId, player.userId())
                .last("FOR UPDATE"));
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "账号不存在");
        }
        if (user.getStatus() == UserStatus.DISABLED) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "已注销账号不能审核");
        }
        if (user.getStatus() == UserStatus.PENDING) {
            userMapper.updateStatus(user.getId(), UserStatus.ACTIVE.code());
            auditRecorder.recordCurrent("USER_APPROVE", "USER", user.getId(),
                    Map.of("status", UserStatus.PENDING.code()),
                    Map.of("status", UserStatus.ACTIVE.code()));
            outboxService.append(actor.teamId(), "USER", user.getId(), EventTypes.ACCOUNT_APPROVED,
                    Map.of("userId", user.getId(), "actorUserId", actor.userId()));
        }
        return playerAdminService.adminView(actor.teamId(), playerId);
    }
}
