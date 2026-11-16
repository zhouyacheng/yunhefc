package com.yunhefc.auth.application;

import com.yunhefc.auth.dto.AuthDtos.AuthResult;
import com.yunhefc.auth.infrastructure.AdminHandoffTicketStore;
import com.yunhefc.common.config.SessionProperties;
import com.yunhefc.common.error.BusinessException;
import com.yunhefc.common.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;

/** C→B 免登录交接：签发短时单次票据，兑换时复核后台权限并建立独立 B 会话。 */
@Service
@RequiredArgsConstructor
public class AdminHandoffService {

    private final AuthService authService;
    private final AdminHandoffTicketStore ticketStore;
    private final SessionProperties sessionProperties;

    public IssuedTicket issue(Long userId) {
        authService.requireBackendEligible(userId);
        Duration ttl = sessionProperties.handoffTtl();
        return new IssuedTicket(ticketStore.save(userId, ttl), ttl);
    }

    /** 先烧毁票据再复核权限，避免重放与探测。 */
    public AuthResult consume(String ticket) {
        Long userId = ticketStore.consume(ticket);
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED, "登录票据无效或已过期");
        }
        return authService.requireBackendEligible(userId);
    }

    public record IssuedTicket(String ticket, Duration ttl) {
    }
}
