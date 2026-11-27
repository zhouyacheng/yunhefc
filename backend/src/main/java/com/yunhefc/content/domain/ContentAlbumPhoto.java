package com.yunhefc.content.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 相册照片；单相册有效照片上限 200 张，对象键不含签名参数。
 */
@Getter
@Setter
@TableName("content_album_photo")
public class ContentAlbumPhoto {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long albumId;
    private String objectKey;
    private Long uploadedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @TableLogic
    private Integer isDeleted;
}
