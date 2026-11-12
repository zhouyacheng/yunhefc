package com.yunhefc.auth.wechat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class RealWechatOAuthClientTest {

    private static final String TOKEN_URL_PREFIX = "https://api.weixin.qq.com/sns/oauth2/access_token";

    private MockRestServiceServer server;
    private RealWechatOAuthClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        WechatProperties properties = new WechatProperties();
        properties.setMode("real");
        properties.setAppId("app-id");
        properties.setAppSecret("app-secret");
        properties.setCallbackUrl("https://www.example.com/api/v1/c/auth/wechat/callback");
        client = new RealWechatOAuthClient(properties, new ObjectMapper(), builder.build());
    }

    @Test
    void returnsOpenidOnSuccess() {
        server.expect(requestTo(startsWith(TOKEN_URL_PREFIX)))
                .andRespond(withSuccess("{\"openid\":\"openid-1\"}", MediaType.APPLICATION_JSON));

        assertThat(client.exchange("code-1").openid()).isEqualTo("openid-1");
        server.verify();
    }

    @Test
    void invalidCode40029MapsToInvalidCode() {
        assertInvalidCode("{\"errcode\":40029,\"errmsg\":\"invalid code\"}");
    }

    @Test
    void invalidCode40163MapsToInvalidCode() {
        assertInvalidCode("{\"errcode\":40163,\"errmsg\":\"code been used\"}");
    }

    @Test
    void otherProviderErrorMapsToProviderError() {
        server.expect(requestTo(startsWith(TOKEN_URL_PREFIX)))
                .andRespond(withSuccess("{\"errcode\":40013,\"errmsg\":\"invalid appid\"}",
                        MediaType.APPLICATION_JSON));

        assertProviderError();
    }

    @Test
    void malformedResponseMapsToProviderError() {
        server.expect(requestTo(startsWith(TOKEN_URL_PREFIX)))
                .andRespond(withSuccess("not-json", MediaType.APPLICATION_JSON));

        assertProviderError();
    }

    @Test
    void httpFailureMapsToProviderError() {
        server.expect(requestTo(startsWith(TOKEN_URL_PREFIX)))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertProviderError();
    }

    @Test
    void doesNotRetryProviderFailure() {
        java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();
        server.expect(requestTo(startsWith(TOKEN_URL_PREFIX)))
                .andRespond(request -> {
                    calls.incrementAndGet();
                    return withStatus(HttpStatus.INTERNAL_SERVER_ERROR).createResponse(request);
                });

        assertThatThrownBy(() -> client.exchange("code-1"))
                .isInstanceOf(WechatOAuthException.class);
        assertThat(calls).hasValue(1);
    }

    @Test
    void authorizeUrlUsesConfiguredCallback() {
        String url = client.authorizeUrl("state-1");

        assertThat(url)
                .startsWith("https://open.weixin.qq.com/connect/oauth2/authorize")
                .contains("appid=app-id")
                .contains("redirect_uri=https%3A%2F%2Fwww.example.com%2Fapi%2Fv1%2Fc%2Fauth%2Fwechat%2Fcallback")
                .contains("scope=snsapi_base")
                .contains("state=state-1");
    }

    private void assertInvalidCode(String body) {
        server.expect(requestTo(startsWith(TOKEN_URL_PREFIX)))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.exchange("code-1"))
                .isInstanceOfSatisfying(WechatOAuthException.class,
                        ex -> assertThat(ex.reason()).isEqualTo(WechatOAuthException.Reason.INVALID_CODE));
        server.verify();
    }

    private void assertProviderError() {
        assertThatThrownBy(() -> client.exchange("code-1"))
                .isInstanceOfSatisfying(WechatOAuthException.class,
                        ex -> assertThat(ex.reason()).isEqualTo(WechatOAuthException.Reason.PROVIDER_ERROR));
        server.verify();
    }
}
