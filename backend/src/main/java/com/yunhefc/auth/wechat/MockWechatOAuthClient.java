package com.yunhefc.auth.wechat;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class MockWechatOAuthClient implements WechatOAuthClient {

    @Override
    public String authorizeUrl(String state) {
        return "/api/v1/c/auth/wechat/mock-authorize?state="
                + URLEncoder.encode(state, StandardCharsets.UTF_8);
    }

    @Override
    public WechatUser exchange(String code) {
        return new WechatUser("mock-openid-" + code);
    }
}
