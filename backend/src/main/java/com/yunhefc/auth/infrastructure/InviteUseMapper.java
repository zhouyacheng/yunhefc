package com.yunhefc.auth.infrastructure;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yunhefc.auth.domain.InviteUse;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface InviteUseMapper extends BaseMapper<InviteUse> {

    @Insert("""
            INSERT IGNORE INTO invite_use (invite_id, user_id)
            VALUES (#{inviteId}, #{userId})
            """)
    int insertIgnore(@Param("inviteId") Long inviteId, @Param("userId") Long userId);

    @Select("""
            SELECT COUNT(*) FROM invite_use
            WHERE invite_id = #{inviteId} AND user_id = #{userId} AND is_deleted = 0
            """)
    int countUse(@Param("inviteId") Long inviteId, @Param("userId") Long userId);
}
