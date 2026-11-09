package com.yunhefc.common.idempotency;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface IdempotencyKeyMapper extends BaseMapper<IdempotencyKey> {

    @Select("""
            SELECT * FROM idempotency_key
            WHERE user_id = #{userId} AND endpoint = #{endpoint} AND idem_key = #{idemKey}
              AND is_deleted = 0
            LIMIT 1
            """)
    IdempotencyKey find(@Param("userId") Long userId, @Param("endpoint") String endpoint,
                        @Param("idemKey") String idemKey);

    @Delete("DELETE FROM idempotency_key WHERE id = #{id}")
    int deletePhysical(@Param("id") Long id);
}
