package com.yunhefc.auth.infrastructure;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yunhefc.auth.domain.Team;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface TeamMapper extends BaseMapper<Team> {

    /**
     * 单队 MVP 以球队行作为串行化锁，用于“最后管理员”等球队级不变量的并发校验。
     */
    @Select("SELECT id FROM team WHERE is_deleted = 0 ORDER BY id ASC LIMIT 1 FOR UPDATE")
    Long lockTeamId();
}
