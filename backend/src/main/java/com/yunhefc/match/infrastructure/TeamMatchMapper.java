package com.yunhefc.match.infrastructure;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yunhefc.match.domain.TeamMatch;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TeamMatchMapper extends BaseMapper<TeamMatch> {
}
