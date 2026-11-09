package com.yunhefc.infra.outbox;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface OutboxEventMapper extends BaseMapper<OutboxEvent> {

    @Select("""
            SELECT * FROM outbox_event
            WHERE status = 0 AND is_deleted = 0
              AND (next_retry_at IS NULL OR next_retry_at <= NOW(3))
              AND (lease_until IS NULL OR lease_until < NOW(3))
            ORDER BY id
            LIMIT #{limit}
            """)
    List<OutboxEvent> findClaimable(@Param("limit") int limit);

    @Update("""
            UPDATE outbox_event
            SET lease_owner = #{owner}, lease_until = #{leaseUntil}
            WHERE id = #{id} AND status = 0 AND is_deleted = 0
              AND (lease_until IS NULL OR lease_until < NOW(3))
            """)
    int claim(@Param("id") Long id, @Param("owner") String owner,
              @Param("leaseUntil") LocalDateTime leaseUntil);

    @Update("""
            UPDATE outbox_event
            SET status = 1, sent_at = NOW(3), lease_owner = NULL, lease_until = NULL, last_error = NULL
            WHERE id = #{id}
            """)
    int markSent(@Param("id") Long id);

    @Update("""
            UPDATE outbox_event
            SET attempts = attempts + 1,
                next_retry_at = #{nextRetryAt},
                lease_owner = NULL,
                lease_until = NULL,
                last_error = #{error},
                status = CASE WHEN attempts + 1 >= #{maxAttempts} THEN 2 ELSE 0 END
            WHERE id = #{id}
            """)
    int markFailed(@Param("id") Long id, @Param("nextRetryAt") LocalDateTime nextRetryAt,
                   @Param("error") String error, @Param("maxAttempts") int maxAttempts);
}
