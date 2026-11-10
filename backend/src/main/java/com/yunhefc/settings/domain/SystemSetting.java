package com.yunhefc.settings.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@TableName("system_setting")
public class SystemSetting {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long teamId;
    private String settingKey;
    private String settingValue;
    private String valueType;
    private Long updatedBy;
    private Instant createdAt;
    private Instant updatedAt;
    @TableLogic
    private Integer isDeleted;
}
