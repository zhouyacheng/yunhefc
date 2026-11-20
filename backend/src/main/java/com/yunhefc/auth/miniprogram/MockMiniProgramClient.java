package com.yunhefc.auth.miniprogram;

public class MockMiniProgramClient implements MiniProgramClient {

    @Override
    public WechatSession exchange(String code) {
        return new WechatSession("mock-openid-" + code);
    }
}
