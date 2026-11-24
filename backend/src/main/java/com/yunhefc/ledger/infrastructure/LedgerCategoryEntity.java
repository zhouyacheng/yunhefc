package com.yunhefc.ledger.infrastructure;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.yunhefc.ledger.domain.LedgerCategory;
import com.yunhefc.ledger.domain.LedgerType;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * ledger_category 的持久化映射；领域层不依赖 MyBatis（java-rules §3.3）。
 */
@Getter
@Setter
@TableName("ledger_category")
public class LedgerCategoryEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long teamId;
    private String name;
    private LedgerType direction;
    private Integer sortNo;
    private Boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @TableLogic
    private Integer isDeleted;

    public static LedgerCategoryEntity from(LedgerCategory category) {
        LedgerCategoryEntity entity = new LedgerCategoryEntity();
        entity.setId(category.getId());
        entity.setTeamId(category.getTeamId());
        entity.setName(category.getName());
        entity.setDirection(category.getDirection());
        entity.setSortNo(category.getSortNo());
        entity.setIsActive(category.getIsActive());
        entity.setCreatedAt(category.getCreatedAt());
        entity.setUpdatedAt(category.getUpdatedAt());
        entity.setIsDeleted(category.getIsDeleted());
        return entity;
    }

    public LedgerCategory toDomain() {
        LedgerCategory category = new LedgerCategory();
        category.setId(id);
        category.setTeamId(teamId);
        category.setName(name);
        category.setDirection(direction);
        category.setSortNo(sortNo);
        category.setIsActive(isActive);
        category.setCreatedAt(createdAt);
        category.setUpdatedAt(updatedAt);
        category.setIsDeleted(isDeleted);
        return category;
    }
}
