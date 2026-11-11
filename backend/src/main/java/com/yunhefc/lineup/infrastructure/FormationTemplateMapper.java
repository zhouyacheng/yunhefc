package com.yunhefc.lineup.infrastructure;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yunhefc.lineup.domain.FormationTemplate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface FormationTemplateMapper extends BaseMapper<FormationTemplate> {

    // 历史阵容按 formation_template_id 读取被软删除的模板，不受 @TableLogic 过滤。
    @Select("""
            SELECT id, team_id, name, is_preset, base_template_id, created_by,
                   created_at, updated_at, is_deleted
            FROM formation_template
            WHERE id = #{id}
            """)
    FormationTemplate selectByIdIncludingDeleted(@Param("id") Long id);
}
