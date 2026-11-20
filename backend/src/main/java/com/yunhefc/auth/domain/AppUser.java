package com.yunhefc.auth.domain;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@TableName("app_user")
public class AppUser {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String phone;
    private String passwordHash;
    private String openid;
    private String mpOpenid;
    private UserStatus status;
    private LocalDateTime lastLoginAt;
    private LocalDateTime createdAt;
    @TableField(updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime updatedAt;
    @TableLogic
    private Integer isDeleted;

    /** 过渡期：公众号 openid 与小程序 mp_openid 任一非空即视为已绑定。 */
    public boolean isWechatBound() {
        return notBlank(openid) || notBlank(mpOpenid);
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
