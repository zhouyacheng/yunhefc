package com.yunhefc.ledger.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 收支分类；direction 为 NULL 表示通用分类，仅停用不删除。
 */
@Getter
@Setter
@TableName("ledger_category")
public class LedgerCategory {

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
}
