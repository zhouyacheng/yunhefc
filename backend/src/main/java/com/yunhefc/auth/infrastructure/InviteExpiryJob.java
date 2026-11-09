package com.yunhefc.auth.infrastructure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class InviteExpiryJob {

    private static final Logger log = LoggerFactory.getLogger(InviteExpiryJob.class);

    private final InviteLinkMapper inviteLinkMapper;

    public InviteExpiryJob(InviteLinkMapper inviteLinkMapper) {
        this.inviteLinkMapper = inviteLinkMapper;
    }

    @Scheduled(cron = "${yunhefc.invite.expiry-cron:0 0 3 * * *}", zone = "Asia/Shanghai")
    public void expire() {
        int expired = inviteLinkMapper.expireOverdue(LocalDateTime.now());
        if (expired > 0) {
            log.info("Expired {} invite links", expired);
        }
    }
}
