package com.yunhefc.common.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "yunhefc.retention")
public class RetentionProperties {

    @Min(1)
    private int notificationDays = 90;
    @Min(1)
    private int inviteDays = 30;
    @Min(1)
    private int outboxDays = 7;
    @Min(1)
    private int consumedEventDays = 7;

    public int getNotificationDays() {
        return notificationDays;
    }

    public void setNotificationDays(int notificationDays) {
        this.notificationDays = notificationDays;
    }

    public int getInviteDays() {
        return inviteDays;
    }

    public void setInviteDays(int inviteDays) {
        this.inviteDays = inviteDays;
    }

    public int getOutboxDays() {
        return outboxDays;
    }

    public void setOutboxDays(int outboxDays) {
        this.outboxDays = outboxDays;
    }

    public int getConsumedEventDays() {
        return consumedEventDays;
    }

    public void setConsumedEventDays(int consumedEventDays) {
        this.consumedEventDays = consumedEventDays;
    }
}
