package com.yunhefc.content.infrastructure;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yunhefc.content.domain.ContentPostLike;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.util.Collection;
import java.util.List;

@Mapper
public interface ContentPostLikeMapper extends BaseMapper<ContentPostLike> {

    /**
     * 点赞状态幂等：不存在则插入，已取消（is_deleted=1）则恢复同一行。
     */
    @Insert("""
            INSERT INTO content_post_like (post_id, user_id)
            VALUES (#{postId}, #{userId})
            ON DUPLICATE KEY UPDATE is_deleted = 0
            """)
    int upsertLike(@Param("postId") Long postId, @Param("userId") Long userId);

    @Update("""
            UPDATE content_post_like SET is_deleted = 1
            WHERE post_id = #{postId} AND user_id = #{userId} AND is_deleted = 0
            """)
    int removeLike(@Param("postId") Long postId, @Param("userId") Long userId);

    List<ContentPostLikeCountRow> selectActiveCounts(@Param("postIds") Collection<Long> postIds);

    List<Long> selectLikedPostIds(@Param("userId") Long userId,
                                  @Param("postIds") Collection<Long> postIds);
}
