package com.yunhefc.activity.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yunhefc.activity.domain.ActivityImage;
import com.yunhefc.activity.infrastructure.ActivityImageMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 活动图片读写：读取按 sort_no 升序；编辑采用覆盖式，旧行伪删除后按顺序批量插入新行。
 */
@Service
@RequiredArgsConstructor
public class ActivityImageStore {

    private final ActivityImageMapper activityImageMapper;

    public List<String> keys(Long activityId) {
        return activityImageMapper.selectList(orderedQuery()
                .eq(ActivityImage::getActivityId, activityId))
                .stream()
                .map(ActivityImage::getImageKey)
                .toList();
    }

    public Map<Long, List<String>> keysByActivity(Collection<Long> activityIds) {
        if (activityIds.isEmpty()) {
            return Map.of();
        }
        return activityImageMapper.selectList(orderedQuery()
                        .in(ActivityImage::getActivityId, activityIds))
                .stream()
                .collect(Collectors.groupingBy(ActivityImage::getActivityId, LinkedHashMap::new,
                        Collectors.mapping(ActivityImage::getImageKey, Collectors.toList())));
    }

    /** 顺序与内容相同时不写库；否则旧行全部伪删除后按新顺序插入。 */
    public void replace(Long activityId, List<String> imageKeys) {
        List<String> target = imageKeys == null ? List.of() : imageKeys;
        if (keys(activityId).equals(target)) {
            return;
        }
        activityImageMapper.delete(new LambdaQueryWrapper<ActivityImage>()
                .eq(ActivityImage::getActivityId, activityId));
        if (target.isEmpty()) {
            return;
        }
        List<ActivityImage> images = new ArrayList<>(target.size());
        for (int sortNo = 0; sortNo < target.size(); sortNo++) {
            ActivityImage image = new ActivityImage();
            image.setActivityId(activityId);
            image.setImageKey(target.get(sortNo));
            image.setSortNo(sortNo);
            images.add(image);
        }
        activityImageMapper.insertBatch(images);
    }

    private LambdaQueryWrapper<ActivityImage> orderedQuery() {
        return new LambdaQueryWrapper<ActivityImage>()
                .orderByAsc(ActivityImage::getActivityId)
                .orderByAsc(ActivityImage::getSortNo)
                .orderByAsc(ActivityImage::getId);
    }
}
