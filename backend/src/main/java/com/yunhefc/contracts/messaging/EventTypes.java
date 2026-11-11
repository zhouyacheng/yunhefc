package com.yunhefc.contracts.messaging;

import java.util.Set;

public final class EventTypes {

    public static final String AUDIT_RECORDED = "AUDIT_RECORDED";
    public static final String ROLE_CHANGED = "ROLE_CHANGED";
    public static final String ACCOUNT_APPROVED = "ACCOUNT_APPROVED";
    public static final String ACCOUNT_DISABLED = "ACCOUNT_DISABLED";
    public static final String REGISTRATION_CONFIRMED = "REGISTRATION_CONFIRMED";
    public static final String WAITLIST_PROMOTED = "WAITLIST_PROMOTED";

    /**
     * 投递到通知 topic 并被站内信消费者处理的领域事件；新增类型时只维护此处。
     */
    public static final Set<String> NOTIFICATION_EVENTS = Set.of(
            ROLE_CHANGED, ACCOUNT_APPROVED, ACCOUNT_DISABLED,
            REGISTRATION_CONFIRMED, WAITLIST_PROMOTED);

    private EventTypes() {
    }
}
