package com.yunhefc.auth.application;

import com.yunhefc.auth.infrastructure.InviteLinkMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class InviteExpiryService {

    private static final Logger log = LoggerFactory.getLogger(InviteExpiryService.class);

    private final InviteLinkMapper inviteLinkMapper;
    private final Clock clock;

    public int expire() {
        int expired = inviteLinkMapper.expireOverdue(Instant.now(clock));
        if (expired > 0) {
            log.debug("Expired invite links, count={}", expired);
        }
        return expired;
    }
}
