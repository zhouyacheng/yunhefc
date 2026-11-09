package com.yunhefc.auth.wechat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class RealWechatOAuthClient implements WechatOAuthClient {

    private static final Logger log = LoggerFactory.getLogger(RealWechatOAuthClient.class);

    private final WechatProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public RealWechatOAuthClient(WechatProperties properties, ObjectMapper objectMapper, RestClient restClient) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.restClient = restClient;
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
            if (node.has("errcode")) {
                log.warn("wechat token exchange rejected, errcode={}", node.get("errcode").asInt());
            }
        } catch (Exception ex) {
            log.warn("wechat token exchange response parse failed", ex);
        }
        throw new IllegalStateException("微信授权失败");
    }

    private String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }
}
