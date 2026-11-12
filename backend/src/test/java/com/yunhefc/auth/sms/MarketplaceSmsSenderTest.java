package com.yunhefc.auth.sms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import com.yunhefc.common.config.SmsProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

class MarketplaceSmsSenderTest {

    private HttpServer server;
    private SmsProperties properties;
    private final AtomicReference<String> rawQuery = new AtomicReference<>();
    private final AtomicReference<String> authorization = new AtomicReference<>();
    private final AtomicInteger requests = new AtomicInteger();
    private boolean contextCreated;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        respond(200, "{\"code\":\"0\",\"msg\":\"成功\"}");
        server.start();

        properties = new SmsProperties();
        properties.setEndpoint("http://127.0.0.1:" + server.getAddress().getPort() + "/sms/smsSend");
        properties.setAppCode("app-code");
        properties.setTemplateId("template-id");
        properties.setSignId("sign-id");
        properties.setTtlSeconds(300);
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void postsQueryParametersWithAppCodeAuthorization() {
        new MarketplaceSmsSender(properties).send("13800002049", "123456");

        assertThat(authorization.get()).isEqualTo("APPCODE app-code");
        String query = rawQuery.get();
        assertThat(query).contains("mobile=13800002049");
        assertThat(query).contains("templateId=template-id");
        assertThat(query).contains("smsSignId=sign-id");
        assertThat(URLDecoder.decode(query, StandardCharsets.UTF_8))
                .contains("param=**code**:123456,**minute**:5");
    }

    @Test
    void rejectsNonSuccessHttpResponse() {
        respond(502, "provider error");

        assertThatThrownBy(() -> new MarketplaceSmsSender(properties).send("13800002049", "123456"))
                .isInstanceOfSatisfying(SmsSendException.class,
                        ex -> assertThat(ex.reason()).isEqualTo(SmsSendException.Reason.PROVIDER_ERROR));
    }

    @Test
    void rejectsBusinessFailureBody() {
        respond(200, "{\"code\":\"1204\",\"msg\":\"签名未报备\"}");

        assertThatThrownBy(() -> new MarketplaceSmsSender(properties).send("13800002049", "123456"))
                .isInstanceOfSatisfying(SmsSendException.class,
                        ex -> assertThat(ex.reason()).isEqualTo(SmsSendException.Reason.PROVIDER_ERROR));
    }

    @Test
    void http429MapsToThrottledWithRetryAfter() {
        respond(429, "{\"code\":\"429\"}", "30");

        assertThatThrownBy(() -> new MarketplaceSmsSender(properties).send("13800002049", "123456"))
                .isInstanceOfSatisfying(SmsSendException.class, ex -> {
                    assertThat(ex.reason()).isEqualTo(SmsSendException.Reason.THROTTLED);
                    assertThat(ex.retryAfterSeconds()).isEqualTo(30);
                });
    }

    @Test
    void http429WithoutRetryAfterUsesDefault() {
        respond(429, "{\"code\":\"429\"}");

        assertThatThrownBy(() -> new MarketplaceSmsSender(properties).send("13800002049", "123456"))
                .isInstanceOfSatisfying(SmsSendException.class, ex -> {
                    assertThat(ex.reason()).isEqualTo(SmsSendException.Reason.THROTTLED);
                    assertThat(ex.retryAfterSeconds()).isEqualTo(60);
                });
    }

    @Test
    void sendsExactlyOnce() {
        new MarketplaceSmsSender(properties).send("13800002049", "123456");

        assertThat(requests.get()).isEqualTo(1);
    }

    @Test
    void rejectsIncompleteConfiguration() {
        properties.setAppCode("");

        assertThatThrownBy(() -> new MarketplaceSmsSender(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SMS_APPCODE");
    }

    @Test
    void rejectsInvalidEndpointAtStartup() {
        properties.setEndpoint("https://example.com/sms smsSend");

        assertThatThrownBy(() -> new MarketplaceSmsSender(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SMS_ENDPOINT");
    }

    @Test
    void businessThrottleUsesRetryAfterHeader() {
        respond(200, "{\"code\":\"RATE_LIMIT\"}", "25");

        assertThatThrownBy(() -> new MarketplaceSmsSender(properties).send("13800002049", "123456"))
                .isInstanceOfSatisfying(SmsSendException.class, ex -> {
                    assertThat(ex.reason()).isEqualTo(SmsSendException.Reason.THROTTLED);
                    assertThat(ex.retryAfterSeconds()).isEqualTo(25);
                });
    }

    private void respond(int status, String body) {
        respond(status, body, null);
    }

    private void respond(int status, String body, String retryAfter) {
        if (contextCreated) {
            server.removeContext("/sms/smsSend");
        }
        server.createContext("/sms/smsSend", exchange -> {
            requests.incrementAndGet();
            rawQuery.set(exchange.getRequestURI().getRawQuery());
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            if (retryAfter != null) {
                exchange.getResponseHeaders().add("Retry-After", retryAfter);
            }
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        contextCreated = true;
    }
}
