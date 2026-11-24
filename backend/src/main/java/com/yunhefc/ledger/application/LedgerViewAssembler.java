package com.yunhefc.ledger.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yunhefc.contracts.activity.ActivityReadPort;
import com.yunhefc.contracts.activity.ActivitySnapshot;
import com.yunhefc.contracts.player.PlayerReadPort;
import com.yunhefc.contracts.player.PlayerSnapshot;
import com.yunhefc.ledger.domain.LedgerCategory;
import com.yunhefc.ledger.domain.LedgerEntry;
import com.yunhefc.ledger.domain.LedgerType;
import com.yunhefc.ledger.dto.LedgerDtos.LedgerCategoryView;
import com.yunhefc.ledger.dto.LedgerDtos.LedgerEntryView;
import com.yunhefc.ledger.infrastructure.LedgerCategoryMapper;
import com.yunhefc.ledger.infrastructure.LedgerCategoryRow;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 流水展示对象装配：分类、球员与活动名称按批读取，避免 N+1。
 */
@Service
@RequiredArgsConstructor
class LedgerViewAssembler {

    private final LedgerCategoryMapper categoryMapper;
    private final PlayerReadPort playerReadPort;
    private final ActivityReadPort activityReadPort;

    List<LedgerEntryView> views(Long teamId, List<LedgerEntry> entries) {
        if (entries.isEmpty()) {
            return List.of();
        }
        Map<Long, LedgerCategory> categories = categories(teamId,
                entries.stream().map(LedgerEntry::getCategoryId).collect(Collectors.toSet()));
        Set<Long> playerIds = entries.stream().map(LedgerEntry::getPlayerId)
                .filter(id -> id != null).collect(Collectors.toSet());
        Set<Long> activityIds = entries.stream().map(LedgerEntry::getActivityId)
                .filter(id -> id != null).collect(Collectors.toSet());
        Map<Long, String> playerNames = playerIds.isEmpty() ? Map.of()
                : playerReadPort.findByIds(teamId, playerIds).stream()
                        .collect(Collectors.toMap(PlayerSnapshot::id, PlayerSnapshot::name));
        Map<Long, ActivitySnapshot> activities = activityIds.isEmpty() ? Map.of()
                : activityReadPort.findByIds(teamId, activityIds);
        return entries.stream()
                .map(entry -> view(entry, categories.get(entry.getCategoryId()),
                        get(playerNames, entry.getPlayerId()),
                        getTitle(activities, entry.getActivityId())))
                .toList();
    }

    LedgerEntryView view(Long teamId, LedgerEntry entry) {
        LedgerCategory category = categoryMapper.selectByIdAndTeam(entry.getCategoryId(), teamId);
        String playerName = entry.getPlayerId() == null ? null
                : playerReadPort.findById(entry.getPlayerId())
                        .filter(player -> teamId.equals(player.teamId()))
                        .map(PlayerSnapshot::name)
                        .orElse(null);
        String activityTitle = entry.getActivityId() == null ? null
                : activityReadPort.findById(teamId, entry.getActivityId())
                        .map(ActivitySnapshot::title)
                        .orElse(null);
        return view(entry, category, playerName, activityTitle);
    }

    LedgerCategoryView categoryView(LedgerCategoryRow row) {
        return new LedgerCategoryView(
                row.getId(),
                row.getName(),
                row.getDirection(),
                row.getDirection() == null ? null : LedgerType.of(row.getDirection()).label(),
                row.getSortNo(),
                Boolean.TRUE.equals(row.getIsActive()),
                row.getEntryCount() == null ? 0L : row.getEntryCount());
    }

    LedgerCategoryView categoryView(LedgerCategory category, long entryCount) {
        LedgerType direction = category.getDirection();
        return new LedgerCategoryView(
                category.getId(),
                category.getName(),
                direction == null ? null : direction.code(),
                direction == null ? null : direction.label(),
                category.getSortNo(),
                Boolean.TRUE.equals(category.getIsActive()),
                entryCount);
    }

    private LedgerEntryView view(LedgerEntry entry, LedgerCategory category,
                                 String playerName, String activityTitle) {
        return new LedgerEntryView(
                entry.getId(),
                entry.getType().code(),
                entry.getType().label(),
                entry.getCategoryId(),
                category == null ? null : category.getName(),
                category == null || category.getDirection() == null
                        ? null : category.getDirection().code(),
                entry.getAmount(),
                entry.getDescription(),
                entry.getOccurredAt(),
                entry.getPlayerId(),
                playerName,
                entry.getActivityId(),
                activityTitle,
                entry.getVersion());
    }

    private Map<Long, LedgerCategory> categories(Long teamId, Collection<Long> categoryIds) {
        if (categoryIds.isEmpty()) {
            return Map.of();
        }
        return categoryMapper.selectList(new LambdaQueryWrapper<LedgerCategory>()
                        .eq(LedgerCategory::getTeamId, teamId)
                        .in(LedgerCategory::getId, categoryIds))
                .stream()
                .collect(Collectors.toMap(LedgerCategory::getId, Function.identity()));
    }

    private static <V> V get(Map<Long, V> values, Long key) {
        return key == null ? null : values.get(key);
    }

    private static String getTitle(Map<Long, ActivitySnapshot> activities, Long activityId) {
        ActivitySnapshot activity = get(activities, activityId);
        return activity == null ? null : activity.title();
    }
}
