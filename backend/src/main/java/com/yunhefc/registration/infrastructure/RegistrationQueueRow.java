package com.yunhefc.registration.infrastructure;

import lombok.Getter;
import lombok.Setter;

/**
 * 报名队列表分组统计行。
 */
@Getter
@Setter
public class RegistrationQueueRow {

    private Long matchId;
    private Integer confirmedCount;
    private Integer guestCount;
    private Integer waitlistCount;
}
