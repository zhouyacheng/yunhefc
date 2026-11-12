package com.yunhefc.auth.sms;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yunhefc.common.config.SmsProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;

public final class MarketplaceSmsSender implements SmsSender {

    private static final Logger log = LoggerFactory.getLogger(MarketplaceSmsSender.class);
    private static final int DEFAULT_RETRY_AFTER_SECONDS = 60;

    private final SmsProperties properties;
    private final HttpClient client;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public MarketplaceSmsSender(SmsProperties properties) {
        require(properties.getEndpoint(), "SMS_ENDPOINT");
        require(properties.getAppCode(), "SMS_APPCODE");
        require(properties.getTemplateId(), "SMS_TEMPLATE_ID");
        require(properties.getSignId(), "SMS_SIGN_ID");
        try {
            URI.create(properties.getEndpoint());
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("SMS_ENDPOINT 不是合法的 URL");
        }
        this.properties = properties;
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();
    }

    @Override
    public void send(String phone, String code) {
        HttpRequest request;
        try {
            request = HttpRequest.newBuilder(requestUri(phone, code))
                    .timeout(Duration.ofSeconds(5))
                    .header("Authorization", "APPCODE " + properties.getAppCode())
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();
        } catch (IllegalArgumentException ex) {
            log.warn("sms send failed, reason=INVALID_REQUEST");
            throw new SmsSendException(SmsSendException.Reason.PROVIDER_ERROR);
        }
        HttpResponse<String> response;
        try {
            response = client.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.warn("sms send failed, reason=INTERRUPTED");
            throw new SmsSendException(SmsSendException.Reason.PROVIDER_ERROR);
        } catch (IOException ex) {
            log.warn("sms send failed, reason=TRANSPORT, error={}", ex.getClass().getSimpleName());
            throw new SmsSendException(SmsSendException.Reason.PROVIDER_ERROR);
        }

        int status = response.statusCode();
        if (status == 429) {
            log.warn("sms send failed, reason=THROTTLED, status={}", status);
            throw new SmsSendException(SmsSendException.Reason.THROTTLED, retryAfterSeconds(response));
        }
        if (status < 200 || status >= 300) {
            log.warn("sms send failed, reason=PROVIDER_ERROR, status={}", status);
            throw new SmsSendException(SmsSendException.Reason.PROVIDER_ERROR);
        }
        String providerCode = providerCode(response.body());
        if ("429".equals(providerCode) || providerCode.toLowerCase(Locale.ROOT).contains("limit")) {
            log.warn("sms send failed, reason=THROTTLED, providerCode={}", providerCode);
            throw new SmsSendException(SmsSendException.Reason.THROTTLED, retryAfterSeconds(response));
        }
        if (!"0".equals(providerCode)) {
            log.warn("sms send failed, reason=PROVIDER_ERROR, providerCode={}", providerCode);
            throw new SmsSendException(SmsSendException.Reason.PROVIDER_ERROR);
        }
    }

    private URI requestUri(String phone, String code) {
        long minutes = Math.max(1, properties.getTtlSeconds() / 60);
        String param = "**code**:" + code + ",**minute**:" + minutes;
        String query = "mobile=" + encode(phone)
                + "&templateId=" + encode(properties.getTemplateId())
                + "&smsSignId=" + encode(properties.getSignId())
                + "&param=" + encode(param);
        String endpoint = properties.getEndpoint();
        return URI.create(endpoint + (endpoint.contains("?") ? "&" : "?") + query);
    }

    private String providerCode(String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            return root.path("code").asText("");
        } catch (Exception ex) {
            return "";
        }
    }

    private int retryAfterSeconds(HttpResponse<String> response) {
        return response.headers().firstValue("Retry-After")
                .map(value -> {
                    try {
                        return Math.max(1, Integer.parseInt(value.trim()));
                    } catch (NumberFormatException ignored) {
                        return DEFAULT_RETRY_AFTER_SECONDS;
                    }
                })
                .orElse(DEFAULT_RETRY_AFTER_SECONDS);
    }

    private String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private void require(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " 必须配置后才能启用真实短信");
        }
    }
}
