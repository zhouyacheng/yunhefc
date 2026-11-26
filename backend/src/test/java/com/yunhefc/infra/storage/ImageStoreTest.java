package com.yunhefc.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.yunhefc.common.error.BusinessException;
import com.yunhefc.common.error.ErrorCode;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.regex.Pattern;

class ImageStoreTest {

    private static final long MAX_BYTES = 5_242_880L;
    private static final ImagePolicy POLICY =
            new ImagePolicy("avatar", MAX_BYTES, "图片不能超过 5MB");
    private static final Pattern KEY =
            Pattern.compile("^avatar/9/[0-9A-HJKMNP-TV-Z]{26}\\.jpg$");
    private static final byte[] JPEG = {
            (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x01, 0x02, (byte) 0xFF, (byte) 0xD9};

    private final InMemoryOssGateway ossGateway = new InMemoryOssGateway();
    private final ObjectRemoval objectRemoval = new ObjectRemoval(ossGateway);
    private final ImageStore imageStore = new ImageStore(ossGateway, objectRemoval);

    @Test
    void storesImageWithPolicyPrefixAndSignedUrl() {
        StoredImage stored = imageStore.store(file(JPEG), POLICY, 9L);

        assertThat(stored.objectKey()).matches(KEY);
        assertThat(stored.signedUrl()).isEqualTo("mock://oss/" + stored.objectKey());
        assertThat(ossGateway.exists(stored.objectKey())).isTrue();
        assertThat(ossGateway.contentType(stored.objectKey())).isEqualTo("image/jpeg");
    }

    @Test
    void rejectsEmptyFile() {
        assertInvalid(() -> imageStore.store(file(new byte[0]), POLICY, 9L), "文件不能为空");
    }

    @Test
    void rejectsCorruptAndUnsupportedImages() {
        byte[] truncated = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00, 0x00};
        assertInvalid(() -> imageStore.store(file(truncated), POLICY, 9L), "文件内容损坏");

        assertThatThrownBy(() -> imageStore.store(
                file("not an image".getBytes(StandardCharsets.US_ASCII)), POLICY, 9L))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(ErrorCode.UNSUPPORTED_MEDIA_TYPE));
    }

    @Test
    void rejectsUnreadableMultipartFile() throws Exception {
        MultipartFile unreadable = mock(MultipartFile.class);
        when(unreadable.isEmpty()).thenReturn(false);
        when(unreadable.getBytes()).thenThrow(new IOException("read failed"));

        assertInvalid(() -> imageStore.store(unreadable, POLICY, 9L), "文件内容损坏");
    }

    @Test
    void enforcesPolicySizeBoundary() {
        ImagePolicy exact = new ImagePolicy("avatar", JPEG.length, "图片不能超过 5MB");
        assertThatCode(() -> imageStore.store(file(JPEG), exact, 9L)).doesNotThrowAnyException();

        ImagePolicy tooSmall = new ImagePolicy("avatar", JPEG.length - 1, "图片不能超过 5MB");
        assertInvalid(() -> imageStore.store(file(JPEG), tooSmall, 9L), "图片不能超过 5MB");

        ImagePolicy photo = new ImagePolicy("content", JPEG.length - 1, "照片不能超过 5MB");
        assertInvalid(() -> imageStore.store(file(JPEG), photo, 9L), "照片不能超过 5MB");
    }

    @Test
    void enforcesRealFiveMiBPolicyBoundary() {
        ImagePolicy policy = new ImagePolicy("avatar", MAX_BYTES, "图片不能超过 5MB");

        assertThatCode(() -> imageStore.store(file(jpegOfSize((int) MAX_BYTES)), policy, 9L))
                .doesNotThrowAnyException();
        assertInvalid(() -> imageStore.store(file(jpegOfSize((int) MAX_BYTES + 1)), policy, 9L),
                "图片不能超过 5MB");
    }

    @Test
    void mapsPutFailureToInternalError() {
        OssGateway failing = new InMemoryOssGateway() {
            @Override
            public void putObject(String objectKey, byte[] content, String contentType) {
                throw new IllegalStateException("oss unavailable");
            }
        };
        ImageStore failingStore = new ImageStore(failing, new ObjectRemoval(failing));

        assertThatThrownBy(() -> failingStore.store(file(JPEG), POLICY, 9L))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(ErrorCode.INTERNAL_ERROR));
    }

    @Test
    void discardRemovesObjectAndDegradesOnDeleteFailure() {
        StoredImage stored = imageStore.store(file(JPEG), POLICY, 9L);
        stored.discard();
        assertThat(ossGateway.exists(stored.objectKey())).isFalse();

        StoredImage failing = imageStore.store(file(JPEG), POLICY, 9L);
        ossGateway.failDeleteOn(failing.objectKey());
        Logger logger = (Logger) LoggerFactory.getLogger(ObjectRemoval.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            assertThatCode(failing::discard).doesNotThrowAnyException();
        } finally {
            logger.detachAppender(appender);
        }
        assertThat(appender.list).anySatisfy(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.WARN);
            assertThat(event.getFormattedMessage()).contains(failing.objectKey());
        });
    }

    private MockMultipartFile file(byte[] content) {
        return new MockMultipartFile("file", "image.jpg", "image/jpeg", content);
    }

    private byte[] jpegOfSize(int size) {
        byte[] content = new byte[size];
        content[0] = (byte) 0xFF;
        content[1] = (byte) 0xD8;
        content[2] = (byte) 0xFF;
        content[content.length - 2] = (byte) 0xFF;
        content[content.length - 1] = (byte) 0xD9;
        return content;
    }

    private void assertInvalid(Runnable action, String message) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.errorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED);
                    assertThat(ex.data()).isEqualTo(
                            Map.of("details", Map.of("file", message)));
                });
    }
}
