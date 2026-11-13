package com.yunhefc.infra.logging;

import ch.qos.logback.classic.pattern.MessageConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;
import com.yunhefc.common.util.MaskUtils;

/**
 * 文本日志兜底脱敏；ECS JSON 不经过 Pattern，仍需调用点先脱敏。
 */
public class SensitiveMessageConverter extends MessageConverter {

    @Override
    public String convert(ILoggingEvent event) {
        return MaskUtils.maskText(event.getFormattedMessage());
    }
}
