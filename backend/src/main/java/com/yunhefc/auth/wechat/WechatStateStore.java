package com.yunhefc.auth.wechat;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class WechatStateStore {

    private final StringRedisTemplate redis;
    private final WechatProperties properties;

    public void put(String state, String redirect) {
        redis.opsForValue().set(key(state), redirect, Duration.ofSeconds(properties.getStateTtlSeconds()));
    }

    public String consume(String state) {
        return redis.opsForValue().getAndDelete(key(state));
    }

    /** 只读取不消费；HTML 失败页需要保留用户原目标。 */
    public String peek(String state) {
        if (state == null || state.isBlank()) {
            return null;
        }
        return redis.opsForValue().get(key(state));
    }

    private String key(String state) {
        return "yunhefc:auth:wechat:state:" + state;
    }
}
