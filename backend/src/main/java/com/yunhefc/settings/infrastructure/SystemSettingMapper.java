package com.yunhefc.settings.infrastructure;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yunhefc.settings.domain.SystemSetting;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.util.Map;

@Mapper
public interface SystemSettingMapper extends BaseMapper<SystemSetting> {

    /**
     * 按 (team_id, setting_key) 覆盖写入；Integer 参数落为 JSON 数字，而不是字符串。
     */
    @Insert("""
            <script>
            INSERT INTO system_setting
                (team_id, setting_key, setting_value, value_type, updated_by, created_at, updated_at, is_deleted)
            VALUES
            <foreach collection="values" index="key" item="value" separator=",">
                (#{teamId}, #{key}, CAST(#{value} AS JSON), 'NUMBER', #{updatedBy}, #{now}, #{now}, 0)
            </foreach>
            ON DUPLICATE KEY UPDATE
                setting_value = VALUES(setting_value),
                value_type = VALUES(value_type),
                updated_by = VALUES(updated_by),
                updated_at = VALUES(updated_at),
                is_deleted = 0
            </script>
            """)
    int upsertBatch(@Param("teamId") Long teamId, @Param("values") Map<String, Integer> values,
                    @Param("updatedBy") Long updatedBy, @Param("now") Instant now);
}
