package com.yunhefc.auth.application;

import com.yunhefc.audit.application.AuditRecorder;
import com.yunhefc.auth.domain.InviteLink;
import com.yunhefc.auth.domain.InviteStatus;
import com.yunhefc.auth.infrastructure.InviteLinkMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class InviteExpiryService {

    private static final Logger log = LoggerFactory.getLogger(InviteExpiryService.class);

    private final InviteLinkMapper inviteLinkMapper;
    private final AuditRecorder auditRecorder;
    private final Clock clock;

    @Transactional
    public int expire() {
        Instant now = Instant.now(clock);
        List<InviteLink> overdue = inviteLinkMapper.findOverdue(now);
        int expired = 0;
        for (InviteLink invite : overdue) {
            if (inviteLinkMapper.expire(invite.getId()) == 0) {
                continue;
            }
            auditRecorder.record(invite.getTeamId(), null, AuditRecorder.SYSTEM_ACTOR_NAME,
                    "INVITE_EXPIRE", "INVITE", invite.getId(),
                    Map.of("status", InviteStatus.ACTIVE.code()),
                    Map.of("status", InviteStatus.EXPIRED.code()));
            expired++;
        }
        if (expired > 0) {
            log.debug("Expired invite links, count={}", expired);
        }
        return expired;
    }
}
