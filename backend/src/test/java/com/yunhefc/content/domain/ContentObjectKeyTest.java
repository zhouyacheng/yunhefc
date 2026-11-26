package com.yunhefc.content.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yunhefc.common.error.BusinessException;
import com.yunhefc.common.error.ErrorCode;
import org.junit.jupiter.api.Test;

import java.util.Map;

class ContentObjectKeyTest {

    private static final String ULID = "01J9ZQ8K3M4N5P6Q7R8S9T0VWX";

    @Test
    void generatesKeysUnderUploaderPrefix() {
        assertThat(ContentObjectKey.generate(12, ULID, "jpg"))
                .isEqualTo("content/12/" + ULID + ".jpg");
        assertThat(ContentObjectKey.generate(7, ULID, "png"))
                .isEqualTo("content/7/" + ULID + ".png");
        assertThat(ContentObjectKey.generate(123, ULID, "webp"))
                .isEqualTo("content/123/" + ULID + ".webp");
    }

    @Test
    void acceptsUploadedObjectKeys() {
        assertThat(ContentObjectKey.require("content/12/" + ULID + ".jpg"))
                .isEqualTo("content/12/" + ULID + ".jpg");
        assertThat(ContentObjectKey.require("content/7/" + ULID + ".png"))
                .isEqualTo("content/7/" + ULID + ".png");
        assertThat(ContentObjectKey.require("content/123/" + ULID + ".webp"))
                .isEqualTo("content/123/" + ULID + ".webp");
    }

    @Test
    void generateRejectsUnsupportedExtension() {
        assertThatThrownBy(() -> ContentObjectKey.generate(12, ULID, "gif"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VALIDATION_FAILED);
    }

    @Test
    void rejectsOtherPrefixesAndUrls() {
        assertInvalid("avatar/12/" + ULID + ".png");
        assertInvalid("activity/12/" + ULID + ".png");
        assertInvalid("content/12/" + ULID + ".png ");
        assertInvalid("https://oss.example/content/12/" + ULID + ".png");
        assertInvalid(ULID + ".png");
        assertInvalid("content/abc/" + ULID + ".png");
        assertInvalid("content/../" + ULID + ".png");
    }

    @Test
    void rejectsMalformedKeys() {
        assertInvalid(null);
        assertInvalid("");
        assertInvalid("content/12/" + ULID.toLowerCase() + ".png");
        assertInvalid("content/12/" + ULID.substring(0, 25) + ".png");
        assertInvalid("content/12/" + ULID + "X.png");
        assertInvalid("content/12/01J9ZQ8K3M4N5P6Q7R8S9T0VWI.png");
        assertInvalid("content/12/" + ULID + ".jpeg");
        assertInvalid("content/12/" + ULID + ".gif");
        assertInvalid("content/12/" + ULID + ".PNG");
        assertInvalid("content/12/" + ULID + ".png?x-oss-process=image/resize");
    }

    private void assertInvalid(String value) {
        assertThatThrownBy(() -> ContentObjectKey.require(value))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VALIDATION_FAILED)
                .extracting(ex -> ((BusinessException) ex).data())
                .isEqualTo(Map.of("details", Map.of("objectKey", ContentObjectKey.INVALID_MESSAGE)));
    }
}
