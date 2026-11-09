package com.yunhefc.auth.wechat;

public interface WechatOAuthClient {

    String authorizeUrl(String state);

    WechatUser exchange(String code);

    record WechatUser(String openid) {
    }
}
