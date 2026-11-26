package com.yunhefc.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;

import com.yunhefc.content.domain.ContentRules;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class AliyunOssGatewayTest {

    private static final String OBJECT_KEY = "content/12/01J9ZQ8K3M4N5P6Q7R8S9T0VWX.jpg";
    private static final String PROCESS = ContentRules.THUMBNAIL_PROCESS;

    private AliyunOssGateway gateway;

    @AfterEach
    void shutdown() {
        if (gateway != null) {
            gateway.shutdown();
        }
    }

    @Test
    void signsPhotoThumbnailProcessIntoUrl() {
        gateway = gateway();

        String url = gateway.signedUrl(OBJECT_KEY, PROCESS);
        String decoded = URLDecoder.decode(url, StandardCharsets.UTF_8);

        assertThat(url).contains("x-oss-process");
        assertThat(decoded).contains(PROCESS);
        assertThat(decoded).contains(OBJECT_KEY);
        assertThat(ttlSeconds(url)).isBetween(840L, 900L);
    }

    @Test
    void signedUrlWithoutProcessHasNoProcessParameter() {
        gateway = gateway();

        String url = gateway.signedUrl(OBJECT_KEY);

        assertThat(url).doesNotContain("x-oss-process");
        assertThat(ttlSeconds(url)).isBetween(840L, 900L);
    }

    @Test
    void blankProcessBehavesLikeNoProcess() {
        gateway = gateway();

        assertThat(gateway.signedUrl(OBJECT_KEY, " ")).doesNotContain("x-oss-process");
    }

    private AliyunOssGateway gateway() {
        OssProperties properties = new OssProperties();
        properties.setMode("real");
        properties.setEndpoint("https://oss-cn-hangzhou.aliyuncs.com");
        properties.setBucket("yunhefc-test");
        properties.setAccessKeyId("access-key");
        properties.setAccessKeySecret("access-secret");
        return new AliyunOssGateway(properties);
    }

    /**
     * V1 签名返回绝对过期时间 {@code Expires}，V4 签名返回相对秒数 {@code x-oss-expires}。
     */
    private long ttlSeconds(String url) {
        Matcher matcher = Pattern.compile("[?&](Expires|x-oss-expires)=(\\d+)").matcher(url);
        assertThat(matcher.find()).as("signed url carries expiry: %s", url).isTrue();
        long value = Long.parseLong(matcher.group(2));
        return "Expires".equals(matcher.group(1))
                ? value - System.currentTimeMillis() / 1000L
                : value;
    }
}
