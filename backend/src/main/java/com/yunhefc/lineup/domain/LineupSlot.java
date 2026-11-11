package com.yunhefc.lineup.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

/**
 * 阵容槽位；每槽一个 slot_key，替补使用 b1..bn。
 * 保存时按阵容整体物理替换：唯一键不含 is_deleted，伪删除后会与 uk(lineup_id, slot_key)
 * 冲突；与 04 §5.18「随阵容覆盖、不单独删除」一致。
 */
@Getter
@Setter
@TableName("lineup_slot")
public class LineupSlot {

    public static final int STARTER = 0;
    public static final int BENCH = 1;

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long lineupId;
    private String slotKey;
    private Integer slotType;
    private Long playerId;
    private Integer sortOrder;
    @TableLogic
    private Integer isDeleted;
}
