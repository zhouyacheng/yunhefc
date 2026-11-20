package com.yunhefc.auth.miniprogram;

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

class RealMiniProgramClientTest {

    private static final String SESSION_URL_PREFIX = "https://api.weixin.qq.com/sns/jscode2session";

    private MockRestServiceServer server;
    private RealMiniProgramClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        MiniProgramProperties properties = new MiniProgramProperties();
        properties.setMode("real");
        properties.setAppId("wx1234567890abcdef");
        properties.setAppSecret("app-secret");
        client = new RealMiniProgramClient(properties, new ObjectMapper(), builder.build());
    }

    @Test
    void returnsOpenidOnSuccessIgnoringSessionKey() {
        server.expect(requestTo(startsWith(SESSION_URL_PREFIX)))
                .andRespond(withSuccess(
                        "{\"openid\":\"openid-1\",\"session_key\":\"secret-key\",\"unionid\":\"union-1\"}",
                        MediaType.APPLICATION_JSON));

        assertThat(client.exchange("code-1").openid()).isEqualTo("openid-1");
        server.verify();
    }

    @Test
    void invalidCode40029MapsToInvalidCode() {
        assertInvalidCode("{\"errcode\":40029,\"errmsg\":\"invalid code\"}");
    }

    @Test
    void usedCode40163MapsToInvalidCode() {
        assertInvalidCode("{\"errcode\":40163,\"errmsg\":\"code been used\"}");
    }

    @Test
    void otherProviderErrorMapsToProviderError() {
        server.expect(requestTo(startsWith(SESSION_URL_PREFIX)))
                .andRespond(withSuccess("{\"errcode\":40013,\"errmsg\":\"invalid appid\"}",
                        MediaType.APPLICATION_JSON));

        assertProviderError();
    }

    @Test
    void blankOpenidMapsToProviderError() {
        server.expect(requestTo(startsWith(SESSION_URL_PREFIX)))
                .andRespond(withSuccess("{\"openid\":\"\"}", MediaType.APPLICATION_JSON));

        assertProviderError();
    }

    @Test
    void malformedResponseMapsToProviderError() {
        server.expect(requestTo(startsWith(SESSION_URL_PREFIX)))
                .andRespond(withSuccess("not-json", MediaType.APPLICATION_JSON));

        assertProviderError();
    }

    @Test
    void httpFailureMapsToProviderError() {
        server.expect(requestTo(startsWith(SESSION_URL_PREFIX)))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertProviderError();
    }

    private void assertInvalidCode(String body) {
        server.expect(requestTo(startsWith(SESSION_URL_PREFIX)))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.exchange("code-1"))
                .isInstanceOf(MiniProgramException.class)
                .extracting(ex -> ((MiniProgramException) ex).reason())
                .isEqualTo(MiniProgramException.Reason.INVALID_CODE);
    }

    private void assertProviderError() {
        assertThatThrownBy(() -> client.exchange("code-1"))
                .isInstanceOf(MiniProgramException.class)
                .extracting(ex -> ((MiniProgramException) ex).reason())
                .isEqualTo(MiniProgramException.Reason.PROVIDER_ERROR);
    }
}
