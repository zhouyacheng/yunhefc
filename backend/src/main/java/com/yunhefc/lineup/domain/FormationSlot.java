package com.yunhefc.lineup.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 阵型槽位；坐标范围 0–100，role 与球员位置枚举一致。
 */
@Getter
@Setter
@TableName("formation_slot")
public class FormationSlot {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long templateId;
    private String slotKey;
    private Integer role;
    private BigDecimal x;
    private BigDecimal y;
    private Integer sortOrder;
    @TableLogic
    private Integer isDeleted;
}
