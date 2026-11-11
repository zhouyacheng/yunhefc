package com.yunhefc.common.ratelimit;

import com.yunhefc.common.api.RequestContext;
import com.yunhefc.common.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 必须早于 IdempotentAspect：幂等重放与失败请求同样计入窗口。
 * Order 需晚于 ExposeInvocationInterceptor（HIGHEST_PRECEDENCE + 1），否则注解参数无法绑定。
 * Redis 不可用时降级放行，不阻塞主流程。
 */
@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@RequiredArgsConstructor
public class RateLimitedAspect {

    private static final Logger log = LoggerFactory.getLogger(RateLimitedAspect.class);

    private final RedisRateLimiter rateLimiter;

    @Around("@annotation(rateLimited)")
    public Object around(ProceedingJoinPoint joinPoint, RateLimited rateLimited) throws Throwable {
        String key = rateLimited.key() + ":" + CurrentUser.principal().userId()
                + ":" + RequestContext.clientIp();
        try {
            rateLimiter.check(key, rateLimited.limit(),
                    Duration.ofSeconds(rateLimited.windowSeconds()));
        } catch (DataAccessException ex) {
            log.warn("rate limit unavailable, request allowed, requestId={}",
                    RequestContext.requestId(), ex);
        }
        return joinPoint.proceed();
    }
}
