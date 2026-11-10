package com.yunhefc.common.idempotency;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yunhefc.common.api.ApiResponse;
import com.yunhefc.common.error.BusinessException;
import com.yunhefc.common.error.ErrorCode;
import com.yunhefc.common.security.CurrentUser;
import com.yunhefc.common.security.Hashing;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Aspect
@Component
@RequiredArgsConstructor
public class IdempotentAspect {

    private final IdempotencyService idempotencyService;
    private final ObjectMapper objectMapper;

    @Around("@annotation(idempotent)")
    public Object around(ProceedingJoinPoint joinPoint, Idempotent idempotent) throws Throwable {
        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes())
                .getRequest();
        String idemKey = request.getHeader("Idempotency-Key");
        if (idemKey == null || idemKey.isBlank() || idemKey.length() > 64) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "缺少或非法的 Idempotency-Key");
        }
        String requestHash = Hashing.sha256(idempotent.value() + ":" + request.getRequestURI()
                + ":" + objectMapper.writeValueAsString(joinPoint.getArgs()));
        Long userId = CurrentUser.principal().userId();
        return idempotencyService.execute(userId, idempotent.value(), idemKey, requestHash, () -> {
            try {
                return (ApiResponse<?>) joinPoint.proceed();
            } catch (RuntimeException | Error ex) {
                throw ex;
            } catch (Throwable ex) {
                throw new RuntimeException(ex);
            }
        });
    }

}
