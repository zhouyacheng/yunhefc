package com.yunhefc.notification.infrastructure;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yunhefc.notification.domain.Notification;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface NotificationMapper extends BaseMapper<Notification> {

    @Select("""
            SELECT COUNT(*) FROM notification
            WHERE user_id = #{userId} AND read_at IS NULL AND is_deleted = 0
            """)
    int countUnread(@Param("userId") Long userId);
}
