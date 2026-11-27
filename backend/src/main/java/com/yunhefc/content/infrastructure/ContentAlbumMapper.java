package com.yunhefc.content.infrastructure;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yunhefc.content.domain.ContentAlbum;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ContentAlbumMapper extends BaseMapper<ContentAlbum> {

    /**
     * 当前读并锁定相册行：上传计数与封面变更互相串行，保证不超过 200 张。
     */
    @Select("""
            SELECT id, team_id, title, match_id, activity_id, cover_photo_id, created_by,
                   created_at, updated_at, is_deleted
            FROM content_album
            WHERE id = #{id} AND team_id = #{teamId} AND is_deleted = 0
            FOR UPDATE
            """)
    ContentAlbum selectByIdForUpdate(@Param("id") Long id, @Param("teamId") Long teamId);
}
