package com.yunhefc.ledger.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 球队收支流水；金额为正数，收支方向由 type 表达。
 */
@Getter
@Setter
@TableName("team_ledger_entry")
public class LedgerEntry {

    @TableId(type = IdType.AUTO)
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
    @TableLogic
    private Integer isDeleted;
}
