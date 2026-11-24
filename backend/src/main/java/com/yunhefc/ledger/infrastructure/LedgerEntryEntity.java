package com.yunhefc.ledger.infrastructure;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.yunhefc.ledger.domain.LedgerEntry;
import com.yunhefc.ledger.domain.LedgerType;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * team_ledger_entry 的持久化映射；领域层不依赖 MyBatis（java-rules §3.3）。
 */
@Getter
@Setter
@TableName("team_ledger_entry")
public class LedgerEntryEntity {

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

    public static LedgerEntryEntity from(LedgerEntry entry) {
        LedgerEntryEntity entity = new LedgerEntryEntity();
        entity.setId(entry.getId());
        entity.setTeamId(entry.getTeamId());
        entity.setType(entry.getType());
        entity.setCategoryId(entry.getCategoryId());
        entity.setAmount(entry.getAmount());
        entity.setDescription(entry.getDescription());
        entity.setOccurredAt(entry.getOccurredAt());
        entity.setPlayerId(entry.getPlayerId());
        entity.setActivityId(entry.getActivityId());
        entity.setVersion(entry.getVersion());
        entity.setCreatedBy(entry.getCreatedBy());
        entity.setCreatedAt(entry.getCreatedAt());
        entity.setUpdatedAt(entry.getUpdatedAt());
        entity.setIsDeleted(entry.getIsDeleted());
        return entity;
    }

    public LedgerEntry toDomain() {
        LedgerEntry entry = new LedgerEntry();
        entry.setId(id);
        entry.setTeamId(teamId);
        entry.setType(type);
        entry.setCategoryId(categoryId);
        entry.setAmount(amount);
        entry.setDescription(description);
        entry.setOccurredAt(occurredAt);
        entry.setPlayerId(playerId);
        entry.setActivityId(activityId);
        entry.setVersion(version);
        entry.setCreatedBy(createdBy);
        entry.setCreatedAt(createdAt);
        entry.setUpdatedAt(updatedAt);
        entry.setIsDeleted(isDeleted);
        return entry;
    }
}
