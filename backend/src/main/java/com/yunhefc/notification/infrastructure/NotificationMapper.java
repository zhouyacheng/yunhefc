package com.yunhefc.notification.infrastructure;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yunhefc.notification.domain.Notification;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.Instant;
import java.util.List;

@Mapper
public interface NotificationMapper extends BaseMapper<Notification> {

    @Select("""
            SELECT COUNT(*) FROM notification
            WHERE user_id = #{userId} AND read_at IS NULL AND is_deleted = 0
            """)
    int countUnread(@Param("userId") Long userId);

    /**
     * 已读操作用当前读加行锁：并发重复已读时读取先提交的 read_at，而不是事务快照。
     */
    @Select("""
            SELECT id, user_id, type, title, body, ref_type, ref_id, read_at, created_at, is_deleted
            FROM notification
            WHERE id = #{id} AND user_id = #{userId} AND is_deleted = 0
            FOR UPDATE
            """)
    Notification findOwnedForUpdate(@Param("id") Long id, @Param("userId") Long userId);

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

    /**
     * 保留任务用物理删除；自定义 @Delete 绕过 @TableLogic 与默认伪删除。
     */
    @Delete("DELETE FROM notification WHERE created_at < #{before} LIMIT #{limit}")
    int deleteCreatedBefore(@Param("before") Instant before, @Param("limit") int limit);
}
