package com.yunhefc.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.yunhefc.auth.sms.SmsSendException;
import com.yunhefc.auth.sms.SmsSender;
import com.yunhefc.common.config.SmsProperties;
import com.yunhefc.common.error.BusinessException;
import com.yunhefc.common.error.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

class SmsCodeServiceTest {

    private static final String PHONE = "13900000000";
    private static final String KEY = SmsCodeService.key("REGISTER", PHONE);

    @Test
    void realModeStoresGeneratedCodeAfterSuccessfulSend() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        FakeSmsSender sender = new FakeSmsSender();
        SmsCodeService service = new SmsCodeService(redis, properties(false), sender);

        assertThat(service.send(PHONE, "REGISTER")).isEqualTo(300);

        assertThat(sender.codes.get(0)).matches("\\d{6}");
        verify(ops).set(KEY, sender.codes.get(0), Duration.ofSeconds(300));
        verify(redis).delete(KEY + ":attempts");
    }

    @Test
    void senderFailureDoesNotStoreCodeAndIsNotRetried() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        FakeSmsSender sender = new FakeSmsSender();
        sender.failure = new SmsSendException(SmsSendException.Reason.PROVIDER_ERROR);
        SmsCodeService service = new SmsCodeService(redis, properties(false), sender);

        assertThatThrownBy(() -> service.send(PHONE, "REGISTER"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INTERNAL_ERROR);
        assertThat(sender.calls).isEqualTo(1);
        verifyNoInteractions(redis);
    }

    @Test
    void throttledProviderMapsToRateLimitedWithRetryAfter() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        FakeSmsSender sender = new FakeSmsSender();
        sender.failure = new SmsSendException(SmsSendException.Reason.THROTTLED, 42);
        SmsCodeService service = new SmsCodeService(redis, properties(false), sender);

        assertThatThrownBy(() -> service.send(PHONE, "REGISTER"))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.errorCode()).isEqualTo(ErrorCode.RATE_LIMITED);
                    assertThat(ex.data()).isEqualTo(Map.of("retryAfterSeconds", 42));
                });
        verifyNoInteractions(redis);
    }

    @Test
    void mockEnabledWritesFixedCodeWithoutCallingSender() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        FakeSmsSender sender = new FakeSmsSender();
        SmsCodeService service = new SmsCodeService(redis, properties(true), sender);

        assertThat(service.send(PHONE, "REGISTER")).isEqualTo(300);

        verify(ops).set(KEY, "123456", Duration.ofSeconds(300));
        assertThat(sender.calls).isZero();
    }

    @Test
    void allPurposesShareSenderAndUseDistinctKeys() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        FakeSmsSender sender = new FakeSmsSender();
        SmsCodeService service = new SmsCodeService(redis, properties(false), sender);

        service.send(PHONE, "REGISTER");
        service.send(PHONE, "RESET");
        service.send(PHONE, "BIND");

        assertThat(sender.calls).isEqualTo(3);
        verify(ops).set(SmsCodeService.key("REGISTER", PHONE), sender.codes.get(0), Duration.ofSeconds(300));
        verify(ops).set(SmsCodeService.key("RESET", PHONE), sender.codes.get(1), Duration.ofSeconds(300));
        verify(ops).set(SmsCodeService.key("BIND", PHONE), sender.codes.get(2), Duration.ofSeconds(300));
    }

    @Test
    void verifyDeletesCodeAndAttemptsOnSuccess() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.get(KEY)).thenReturn("123456");
        SmsCodeService service = new SmsCodeService(redis, properties(true), new FakeSmsSender());

        service.verify(PHONE, "REGISTER", "123456");

        verify(redis).delete(KEY);
        verify(redis).delete(KEY + ":attempts");
    }

    @Test
    void fiveWrongAttemptsInvalidateCode() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.get(KEY)).thenReturn("123456");
        when(ops.increment(KEY + ":attempts")).thenReturn(1L, 2L, 3L, 4L, 5L);
        SmsCodeService service = new SmsCodeService(redis, properties(true), new FakeSmsSender());

        for (int i = 1; i < 5; i++) {
            assertThatThrownBy(() -> service.verify(PHONE, "REGISTER", "000000"))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VALIDATION_FAILED);
        }
        assertThatThrownBy(() -> service.verify(PHONE, "REGISTER", "000000"))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.errorCode()).isEqualTo(ErrorCode.RATE_LIMITED);
                    assertThat(ex.data()).isEqualTo(Map.of("retryAfterSeconds", 300L));
                });

        verify(redis, org.mockito.Mockito.times(1)).delete(KEY);
    }

    @Test
    void redisKeyDoesNotContainPlainPhone() {
        assertThat(SmsCodeService.key("REGISTER", PHONE))
                .doesNotContain(PHONE)
                .startsWith("yunhefc:auth:sms:REGISTER:");
    }

    private SmsProperties properties(boolean mockEnabled) {
        SmsProperties properties = new SmsProperties();
        properties.setMockEnabled(mockEnabled);
        properties.setMockCode("123456");
        properties.setTtlSeconds(300);
        return properties;
    }

    private static final class FakeSmsSender implements SmsSender {

        private int calls;
        private final List<String> codes = new ArrayList<>();
        private RuntimeException failure;

        @Override
        public void send(String phone, String code) {
            calls++;
            codes.add(code);
            if (failure != null) {
                throw failure;
            }
        }
    }
}
