package com.yunhefc.content.infrastructure;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yunhefc.content.domain.ContentAlbumPhoto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;

@Mapper
public interface ContentAlbumPhotoMapper extends BaseMapper<ContentAlbumPhoto> {

    @Select("""
            SELECT COUNT(*)
            FROM content_album_photo
            WHERE album_id = #{albumId} AND is_deleted = 0
            """)
    int countActive(@Param("albumId") Long albumId);

    List<ContentAlbumPhotoCountRow> selectActiveCounts(@Param("albumIds") Collection<Long> albumIds);

    /**
     * 每个相册一条封面照片：显式封面优先，未设置或已失效时取最早照片。
     */
    List<ContentAlbumPhoto> selectCoverPhotos(@Param("albumIds") Collection<Long> albumIds);
}
