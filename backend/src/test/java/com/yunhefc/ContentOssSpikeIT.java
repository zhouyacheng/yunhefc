package com.yunhefc;

import static org.assertj.core.api.Assertions.assertThat;

import com.yunhefc.common.util.Ulid;
import com.yunhefc.content.domain.ContentRules;
import com.yunhefc.infra.storage.AliyunOssGateway;
import com.yunhefc.infra.storage.OssProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * 真实私有 Bucket spike：默认跳过。设置 OSS_SPIKE_ENABLED=true 并提供 OSS_* 环境变量后手动执行，
 * 验证「PUT 测试图 → 签缩略图 → GET 200 且宽度 ≤480、字节小于原图」，结束后删除测试对象。
 */
@EnabledIfEnvironmentVariable(named = "OSS_SPIKE_ENABLED", matches = "true")
class ContentOssSpikeIT {

    private static final String PROCESS = ContentRules.THUMBNAIL_PROCESS;
    /** 与真实对象键同构（content/{uploaderUserId}/{ULID}.jpg），用不存在的上传人 ID 避免撞上真实照片。 */
    private static final String OBJECT_KEY = "content/999999/" + Ulid.generate() + ".jpg";

    @Test
    void thumbnailProcessShrinksRealPrivateObject() throws Exception {
        AliyunOssGateway gateway = new AliyunOssGateway(properties());
        byte[] original = jpeg(1000, 750);
        try {
            gateway.putObject(OBJECT_KEY, original, "image/jpeg");
            assertThat(gateway.exists(OBJECT_KEY)).isTrue();

            String thumbnailUrl = gateway.signedUrl(OBJECT_KEY, PROCESS);
            assertThat(thumbnailUrl).contains("x-oss-process");

            HttpResponse<byte[]> response = HttpClient.newHttpClient().send(
                    HttpRequest.newBuilder(URI.create(thumbnailUrl)).GET().build(),
                    HttpResponse.BodyHandlers.ofByteArray());

            assertThat(response.statusCode()).as("GET thumbnail").isEqualTo(200);
            BufferedImage thumbnail = ImageIO.read(new ByteArrayInputStream(response.body()));
            assertThat(thumbnail).isNotNull();
            assertThat(thumbnail.getWidth()).isLessThanOrEqualTo(480);
            assertThat(response.body().length).isLessThan(original.length);
        } finally {
            try {
                gateway.deleteObject(OBJECT_KEY);
            } catch (RuntimeException ignored) {
                // 清理失败不应掩盖断言失败；残留测试对象不影响后续运行。
            }
            gateway.shutdown();
        }
    }

    private OssProperties properties() {
        OssProperties properties = new OssProperties();
        properties.setMode("real");
        properties.setEndpoint(requireEnv("OSS_ENDPOINT"));
        properties.setBucket(requireEnv("OSS_BUCKET"));
        properties.setAccessKeyId(requireEnv("OSS_ACCESS_KEY_ID"));
        properties.setAccessKeySecret(requireEnv("OSS_ACCESS_KEY_SECRET"));
        return properties;
    }

    private String requireEnv(String name) {
        String value = System.getenv(name);
        assertThat(value).as("environment variable %s", name).isNotBlank();
        return value;
    }

    private byte[] jpeg(int width, int height) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setPaint(new GradientPaint(0, 0, Color.decode("#1F4D3A"), width, height, Color.decode("#E0B26A")));
            graphics.fillRect(0, 0, width, height);
        } finally {
            graphics.dispose();
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", out);
        return out.toByteArray();
    }
}
