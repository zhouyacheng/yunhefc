package com.yunhefc.player.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yunhefc.common.error.BusinessException;
import com.yunhefc.common.error.ErrorCode;
import org.junit.jupiter.api.Test;

import java.util.Map;

class AvatarObjectKeyTest {

    private static final String ULID = "01J9ZQ8K3M4N5P6Q7R8S9T0VWX";

    @Test
    void acceptsUploadedObjectKeys() {
        assertThat(AvatarObjectKey.normalize("avatar/12/" + ULID + ".jpg"))
                .isEqualTo("avatar/12/" + ULID + ".jpg");
        assertThat(AvatarObjectKey.normalize("avatar/7/" + ULID + ".png"))
                .isEqualTo("avatar/7/" + ULID + ".png");
        assertThat(AvatarObjectKey.normalize("avatar/123/" + ULID + ".webp"))
                .isEqualTo("avatar/123/" + ULID + ".webp");
    }

    @Test
    void nullAndBlankMeanNoAvatar() {
        assertThat(AvatarObjectKey.normalize(null)).isNull();
        assertThat(AvatarObjectKey.normalize("")).isNull();
        assertThat(AvatarObjectKey.normalize("   ")).isNull();
    }

    @Test
    void rejectsKeysWithSurroundingWhitespace() {
        assertInvalid("  avatar/12/" + ULID + ".png  ");
    }

    @Test
    void rejectsUrlsAndMalformedKeys() {
        assertInvalid("https://oss.example/self.png");
        assertInvalid(ULID + ".png");
        assertInvalid("avatar/abc/" + ULID + ".png");
        assertInvalid("avatar/12/" + ULID.toLowerCase() + ".png");
        assertInvalid("avatar/12/" + ULID.substring(0, 25) + ".png");
        assertInvalid("avatar/12/" + ULID + "X.png");
        assertInvalid("avatar/12/01J9ZQ8K3M4N5P6Q7R8S9T0VWI.png");
        assertInvalid("avatar/12/" + ULID + ".jpeg");
        assertInvalid("avatar/12/" + ULID + ".gif");
        assertInvalid("avatar/12/" + ULID + ".PNG");
        assertInvalid("avatar/12/" + ULID + ".png?x=1");
        assertInvalid("avatar/../" + ULID + ".png");
    }

    private void assertInvalid(String value) {
        assertThatThrownBy(() -> AvatarObjectKey.normalize(value))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VALIDATION_FAILED)
                .extracting(ex -> ((BusinessException) ex).data())
                .isEqualTo(Map.of("details", Map.of("avatarUrl", AvatarObjectKey.INVALID_MESSAGE)));
    }
}
