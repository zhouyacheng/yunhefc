package com.yunhefc.activity.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 活动地点图片：每活动最多 10 张，按 sortNo 升序展示；编辑时覆盖式写入。
 */
@Getter
@Setter
@TableName("activity_image")
public class ActivityImage {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long activityId;
    private String imageKey;
    private Integer sortNo;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @TableLogic
    private Integer isDeleted;
}
