package com.yunhefc.ledger.domain;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 账本流水领域对象；表映射由 infrastructure 实体负责。
 */
@Getter
@Setter
public class LedgerEntry {

    private Long id;
    private Long teamId;
    private LedgerType type;
    private Long categoryId;
    private BigDecimal amount;
    private String description;
    private LocalDateTime occurredAt;
    private Long playerId;
    private Long activityId;
    private Integer version;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer isDeleted;
}
