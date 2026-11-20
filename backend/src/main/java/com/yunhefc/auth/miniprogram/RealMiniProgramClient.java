package com.yunhefc.auth.miniprogram;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class RealMiniProgramClient implements MiniProgramClient {

    private static final Logger log = LoggerFactory.getLogger(RealMiniProgramClient.class);

    private final MiniProgramProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public RealMiniProgramClient(MiniProgramProperties properties, ObjectMapper objectMapper, RestClient restClient) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.restClient = restClient;
    }

    @Override
    public WechatSession exchange(String code) {
        String url = "https://api.weixin.qq.com/sns/jscode2session"
                + "?appid=" + encode(properties.getAppId())
                + "&secret=" + encode(properties.getAppSecret())
                + "&js_code=" + encode(code)
                + "&grant_type=authorization_code";
        String body;
        try {
            body = restClient.get().uri(url).retrieve().body(String.class);
        } catch (RestClientException ex) {
            // 异常对象作为最后一个参数，保留堆栈；不记录 URL/body/code。
            log.warn("mini program code2session request failed", ex);
            throw new MiniProgramException(MiniProgramException.Reason.PROVIDER_ERROR);
        }
        JsonNode node;
        try {
            node = body == null || body.isBlank() ? null : objectMapper.readTree(body);
        } catch (Exception ex) {
            log.warn("mini program code2session response parse failed", ex);
            node = null;
        }
        if (node == null || node.isNull()) {
            log.warn("mini program code2session response parse failed");
            throw new MiniProgramException(MiniProgramException.Reason.PROVIDER_ERROR);
        }
        // 只取 openid；session_key 与 unionid 不读取、不保存、不记录。
        if (node.hasNonNull("openid") && !node.get("openid").asText().isBlank()) {
            return new WechatSession(node.get("openid").asText());
        }
        int errcode = node.path("errcode").asInt(0);
        String errmsg = node.path("errmsg").asText("");
        log.warn("mini program code2session rejected, errcode={}, errmsg={}", errcode, errmsg);
        if (errcode == 40029 || errcode == 40163) {
            throw new MiniProgramException(MiniProgramException.Reason.INVALID_CODE);
        }
        throw new MiniProgramException(MiniProgramException.Reason.PROVIDER_ERROR);
    }

    private String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }
}
