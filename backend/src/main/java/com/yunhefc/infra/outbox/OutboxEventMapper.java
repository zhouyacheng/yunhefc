package com.yunhefc.infra.outbox;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.Instant;
import java.util.List;

@Mapper
public interface OutboxEventMapper extends BaseMapper<OutboxEvent> {

    @Select("""
            SELECT id, event_id, team_id, aggregate_type, aggregate_id, event_type, payload_json,
                   status, attempts, next_retry_at, lease_owner, lease_until, last_error,
                   created_at, sent_at, is_deleted
            FROM outbox_event
            WHERE status = 0 AND is_deleted = 0
              AND (next_retry_at IS NULL OR next_retry_at <= NOW(3))
              AND (lease_until IS NULL OR lease_until < NOW(3))
            ORDER BY id
            LIMIT #{limit}
            """)
    List<OutboxEvent> findClaimable(@Param("limit") int limit);

    @Insert("""
            <script>
            INSERT INTO outbox_event
                (event_id, team_id, aggregate_type, aggregate_id, event_type, payload_json,
                 status, attempts, created_at)
            VALUES
            <foreach collection="events" item="event" separator=",">
                (#{event.eventId}, #{event.teamId}, #{event.aggregateType}, #{event.aggregateId},
                 #{event.eventType}, #{event.payloadJson}, #{event.status}, #{event.attempts},
                 #{event.createdAt})
            </foreach>
            </script>
            """)
    int insertBatch(@Param("events") List<OutboxEvent> events);

    @Update("""
            UPDATE outbox_event
            SET lease_owner = #{owner}, lease_until = #{leaseUntil}
            WHERE id = #{id} AND status = 0 AND is_deleted = 0
              AND (lease_until IS NULL OR lease_until < NOW(3))
            """)
    int claim(@Param("id") Long id, @Param("owner") String owner,
              @Param("leaseUntil") Instant leaseUntil);

    @Update("""
            UPDATE outbox_event
            SET status = 1, sent_at = NOW(3), lease_owner = NULL, lease_until = NULL, last_error = NULL
            WHERE id = #{id}
            """)
    int markSent(@Param("id") Long id);

    @Delete("DELETE FROM outbox_event WHERE status = 1 AND sent_at < #{before}")
    int deleteSentBefore(@Param("before") Instant before);

    @Update("""
            UPDATE outbox_event
            SET attempts = attempts + 1,
                next_retry_at = #{nextRetryAt},
                lease_owner = NULL,
                lease_until = NULL,
                last_error = #{error},
                status = CASE WHEN attempts >= #{maxAttempts} THEN 2 ELSE 0 END
            WHERE id = #{id}
            """)
    int markFailed(@Param("id") Long id, @Param("nextRetryAt") Instant nextRetryAt,
                   @Param("error") String error, @Param("maxAttempts") int maxAttempts);
}
