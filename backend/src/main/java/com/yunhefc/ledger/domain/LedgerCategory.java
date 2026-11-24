package com.yunhefc.ledger.domain;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 收支分类领域对象；direction 为 NULL 表示通用分类，只停用不删除。
 */
@Getter
@Setter
public class LedgerCategory {

    private Long id;
    private Long teamId;
    private String name;
    private LedgerType direction;
    private Integer sortNo;
    private Boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer isDeleted;
}
