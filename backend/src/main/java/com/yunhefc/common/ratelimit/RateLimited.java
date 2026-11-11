package com.yunhefc.common.ratelimit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 按账号 + 客户端 IP 限流，窗口 key 为 {key}:{userId}:{clientIp}。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimited {

    String key();

    int limit();

    long windowSeconds();
}
