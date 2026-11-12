package com.yunhefc.auth.application;

import com.yunhefc.auth.infrastructure.InviteLinkMapper;
import com.yunhefc.auth.infrastructure.InviteUseMapper;
import com.yunhefc.common.config.RetentionProperties;
import com.yunhefc.common.retention.RetentionTask;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

@Component
@Order(2)
@RequiredArgsConstructor
public class InviteRetentionAdapter implements RetentionTask {

    private final InviteLinkMapper inviteLinkMapper;
    private final InviteUseMapper inviteUseMapper;
    private final RetentionProperties retentionProperties;
    private final TransactionTemplate transactionTemplate;

    @Override
    public String name() {
        return "invite";
    }

    @Override
    public int deleteBatch(Instant now, int limit) {
        Instant cutoff = now.minus(Duration.ofDays(retentionProperties.getInviteDays()));
        return Objects.requireNonNullElse(transactionTemplate.execute(status -> {
            List<Long> ids = inviteLinkMapper.findPurgeableIds(cutoff, limit);
            if (ids.isEmpty()) {
                return 0;
            }
            inviteUseMapper.deleteByInviteIds(ids);
            return inviteLinkMapper.deleteByIds(ids);
        }), 0);
    }
}
