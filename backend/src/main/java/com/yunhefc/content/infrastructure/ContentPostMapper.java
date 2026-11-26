package com.yunhefc.content.infrastructure;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yunhefc.content.domain.ContentPost;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ContentPostMapper extends BaseMapper<ContentPost> {
}
