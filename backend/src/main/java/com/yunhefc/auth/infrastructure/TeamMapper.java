package com.yunhefc.auth.infrastructure;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yunhefc.auth.domain.Team;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TeamMapper extends BaseMapper<Team> {
}
