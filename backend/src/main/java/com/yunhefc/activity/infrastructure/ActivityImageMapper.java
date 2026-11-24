package com.yunhefc.activity.infrastructure;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yunhefc.activity.domain.ActivityImage;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ActivityImageMapper extends BaseMapper<ActivityImage> {

    /** 覆盖式写入时一次插入最多 10 行，避免循环单条 SQL。 */
    @Insert("""
            <script>
            INSERT INTO activity_image
                (activity_id, image_key, sort_no, created_at, updated_at, is_deleted)
            VALUES
            <foreach collection="images" item="image" separator=",">
                (#{image.activityId}, #{image.imageKey}, #{image.sortNo},
                 CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3), 0)
            </foreach>
            </script>
            """)
    int insertBatch(@Param("images") List<ActivityImage> images);
}
