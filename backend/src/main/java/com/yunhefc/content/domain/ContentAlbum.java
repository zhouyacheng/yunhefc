package com.yunhefc.content.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 相册：可挂 0 或 1 个比赛/活动；封面对外表现为「显式封面 → 最早照片」。
 */
@Getter
@Setter
@TableName("content_album")
public class ContentAlbum {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long teamId;
    private String title;
    private Long matchId;
    private Long activityId;
    private Long coverPhotoId;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @TableLogic
    private Integer isDeleted;
}
