package com.yunhefc.auth.miniprogram;

/**
 * 微信小程序 code2Session 客户端。只消费 openid，不保存 session_key。
 */
public interface MiniProgramClient {

    WechatSession exchange(String code);

    record WechatSession(String openid) {
    }
}
