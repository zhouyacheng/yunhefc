package com.yunhefc.auth.wechat;

public class MockWechatOAuthClient implements WechatOAuthClient {

    @Override
    public String authorizeUrl(String state) {
        return "mock://wechat/authorize?state=" + state;
    }

    @Override
    public WechatUser exchange(String code) {
        return new WechatUser("mock-openid-" + code);
    }
}
