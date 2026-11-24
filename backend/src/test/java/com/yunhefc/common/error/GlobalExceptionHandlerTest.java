package com.yunhefc.common.error;

import static org.assertj.core.api.Assertions.assertThat;

import com.yunhefc.common.api.ApiResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.Map;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void mapsMultipartSizeExceededToValidationFailure() {
        ResponseEntity<ApiResponse<Object>> response =
                handler.handleUploadTooLarge(new MaxUploadSizeExceededException(5_242_880L));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo(ErrorCode.VALIDATION_FAILED.code());
        assertThat(response.getBody().data())
                .isEqualTo(Map.of("details", Map.of("file", "文件超过上传上限")));
    }
}
