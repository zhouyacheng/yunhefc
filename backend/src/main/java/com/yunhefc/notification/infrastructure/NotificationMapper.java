package com.yunhefc.notification.infrastructure;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yunhefc.notification.domain.Notification;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface NotificationMapper extends BaseMapper<Notification> {

    @Select("""
            SELECT COUNT(*) FROM notification
            WHERE user_id = #{userId} AND read_at IS NULL AND is_deleted = 0
            """)
    int countUnread(@Param("userId") Long userId);

    @Insert("""
            <script>
            INSERT INTO notification (user_id, type, title, body, ref_type, ref_id)
            VALUES
            <foreach collection="notifications" item="item" separator=",">
                (#{item.userId}, #{item.type}, #{item.title}, #{item.body},
                 #{item.refType}, #{item.refId})
            </foreach>
            </script>
            """)
    int insertBatch(@Param("notifications") List<Notification> notifications);
}
