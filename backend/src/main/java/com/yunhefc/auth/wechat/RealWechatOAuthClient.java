package com.yunhefc.auth.wechat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

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
        String body;
        try {
            body = restClient.get().uri(url).retrieve().body(String.class);
        } catch (RestClientException ex) {
            log.warn("wechat token exchange request failed");
            throw new WechatOAuthException(WechatOAuthException.Reason.PROVIDER_ERROR);
        }
        JsonNode node;
        try {
            node = body == null || body.isBlank() ? null : objectMapper.readTree(body);
        } catch (Exception ex) {
            node = null;
        }
        if (node == null || node.isNull()) {
            log.warn("wechat token exchange response parse failed");
            throw new WechatOAuthException(WechatOAuthException.Reason.PROVIDER_ERROR);
        }
        if (node.hasNonNull("openid")) {
            return new WechatUser(node.get("openid").asText());
        }
        int errcode = node.path("errcode").asInt(0);
        log.warn("wechat token exchange rejected, errcode={}", errcode);
        if (errcode == 40029 || errcode == 40163) {
            throw new WechatOAuthException(WechatOAuthException.Reason.INVALID_CODE);
        }
        throw new WechatOAuthException(WechatOAuthException.Reason.PROVIDER_ERROR);
    }

    private String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }
}
