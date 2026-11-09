package com.yunhefc.auth.wechat;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class WechatStateStore {

    private final StringRedisTemplate redis;
    private final WechatProperties properties;

    public WechatStateStore(StringRedisTemplate redis, WechatProperties properties) {
        this.redis = redis;
        this.properties = properties;
    }

    public void put(String state, String redirect) {
        redis.opsForValue().set(key(state), redirect, Duration.ofSeconds(properties.getStateTtlSeconds()));
    }

    public String consume(String state) {
        return redis.opsForValue().getAndDelete(key(state));
    }

    private String key(String state) {
        return "yunhefc:auth:wechat:state:" + state;
    }
}
