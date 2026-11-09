package com.yunhefc.auth.wechat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.client.RestClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class RealWechatOAuthClient implements WechatOAuthClient {

    private final WechatProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient = RestClient.create();

    public RealWechatOAuthClient(WechatProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public String authorizeUrl(String state) {
        return "https://open.weixin.qq.com/connect/oauth2/authorize"
                + "?appid=" + encode(properties.getAppId())
                + "&redirect_uri=" + encode(properties.getCallbackUrl())
                + "&response_type=code&scope=snsapi_base&state=" + encode(state)
                + "#wechat_redirect";
    }

    @Override
    public WechatUser exchange(String code) {
        String url = "https://api.weixin.qq.com/sns/oauth2/access_token"
                + "?appid=" + encode(properties.getAppId())
                + "&secret=" + encode(properties.getAppSecret())
                + "&code=" + encode(code)
                + "&grant_type=authorization_code";
        String body = restClient.get().uri(url).retrieve().body(String.class);
        try {
            JsonNode node = objectMapper.readTree(body);
            if (node.hasNonNull("openid")) {
                return new WechatUser(node.get("openid").asText());
            }
        } catch (Exception ignored) {
            // fall through to an explicit error below
        }
        throw new IllegalStateException("微信授权失败");
    }

    private String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }
}
