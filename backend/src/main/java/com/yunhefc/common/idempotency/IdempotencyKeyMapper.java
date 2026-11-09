package com.yunhefc.common.idempotency;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;

@Mapper
public interface IdempotencyKeyMapper extends BaseMapper<IdempotencyKey> {

    @Select("""
            SELECT id, user_id, endpoint, idem_key, request_hash, response_status, response_body,
                   created_at, expires_at, is_deleted
            FROM idempotency_key
            WHERE user_id = #{userId} AND endpoint = #{endpoint} AND idem_key = #{idemKey}
              AND is_deleted = 0
            LIMIT 1
            """)
    IdempotencyKey find(@Param("userId") Long userId, @Param("endpoint") String endpoint,
                        @Param("idemKey") String idemKey);

    @Delete("DELETE FROM idempotency_key WHERE id = #{id}")
    int deletePhysical(@Param("id") Long id);

    @Delete("""
            DELETE FROM idempotency_key
            WHERE id = #{id} AND is_deleted = 0
              AND response_body IS NULL AND created_at < #{staleBefore}
            """)
    int deleteIfStale(@Param("id") Long id, @Param("staleBefore") LocalDateTime staleBefore);
}
