package com.yunhefc.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;

import com.yunhefc.content.domain.ContentRules;
import org.junit.jupiter.api.Test;

class InMemoryOssGatewayTest {

    private static final String OBJECT_KEY = "content/12/01J9ZQ8K3M4N5P6Q7R8S9T0VWX.jpg";
    private static final String PROCESS = ContentRules.THUMBNAIL_PROCESS;

    private final InMemoryOssGateway gateway = new InMemoryOssGateway();

    @Test
    void signedUrlWithProcessCarriesProcess() {
        assertThat(gateway.signedUrl(OBJECT_KEY, PROCESS))
                .isEqualTo("mock://oss/" + OBJECT_KEY + "?x-oss-process=" + PROCESS);
    }

    @Test
    void signedUrlWithoutProcessStaysPlain() {
        assertThat(gateway.signedUrl(OBJECT_KEY)).isEqualTo("mock://oss/" + OBJECT_KEY);
        assertThat(gateway.signedUrl(OBJECT_KEY, null)).isEqualTo("mock://oss/" + OBJECT_KEY);
        assertThat(gateway.signedUrl(OBJECT_KEY, " ")).isEqualTo("mock://oss/" + OBJECT_KEY);
    }

    @Test
    void deleteObjectRemovesStoredObject() {
        gateway.putObject(OBJECT_KEY, new byte[]{1, 2, 3}, "image/jpeg");

        gateway.deleteObject(OBJECT_KEY);

        assertThat(gateway.exists(OBJECT_KEY)).isFalse();
    }
}
