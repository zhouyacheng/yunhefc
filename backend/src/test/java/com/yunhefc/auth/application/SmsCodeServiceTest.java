package com.yunhefc.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yunhefc.common.config.SmsProperties;
import com.yunhefc.common.error.BusinessException;
import com.yunhefc.common.error.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

class SmsCodeServiceTest {

    private static final String PHONE = "13900000000";
    private static final String KEY = "yunhefc:auth:sms:REGISTER:" + PHONE;

    @Test
    void failsClosedWhenMockDisabled() {
        SmsCodeService service = new SmsCodeService(mock(StringRedisTemplate.class), properties(false));

        assertThatThrownBy(() -> service.send(PHONE, "REGISTER"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INTERNAL_ERROR);
        assertThatThrownBy(() -> service.verify(PHONE, "REGISTER", "123456"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INTERNAL_ERROR);
    }

    @Test
    @SuppressWarnings("unchecked")
    void storesAndVerifiesMockCodeWhenEnabled() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.get(KEY)).thenReturn("123456");
        SmsCodeService service = new SmsCodeService(redis, properties(true));

        assertThat(service.send(PHONE, "REGISTER")).isEqualTo(300);
        verify(ops).set(KEY, "123456", Duration.ofSeconds(300));

        service.verify(PHONE, "REGISTER", "123456");
        verify(redis).delete(KEY);
    }

    private SmsProperties properties(boolean mockEnabled) {
        SmsProperties properties = new SmsProperties();
        properties.setMockEnabled(mockEnabled);
        properties.setMockCode("123456");
        properties.setTtlSeconds(300);
        return properties;
    }
}
