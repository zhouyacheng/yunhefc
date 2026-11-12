package com.yunhefc.audit.infrastructure;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yunhefc.audit.domain.AuditLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.Instant;
import java.util.List;

@Mapper
public interface AuditLogMapper extends BaseMapper<AuditLog> {

    /**
     * 冷备要求导出全部原文，显式不加 is_deleted 过滤（审计日志无删除路径）。
     */
    @Select("""
            SELECT id, team_id, actor_user_id, actor_name, action, target_type, target_id,
                   before_json, after_json, request_id, ip, created_at
            FROM audit_log
            WHERE created_at >= #{from} AND created_at < #{to}
            ORDER BY id ASC
            """)
    List<AuditLog> selectForExport(@Param("from") Instant from, @Param("to") Instant to);
}
