package com.yunhefc.ledger.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yunhefc.common.api.ItemsResponse;
import com.yunhefc.common.api.PageQuery;
import com.yunhefc.common.api.PageResponse;
import com.yunhefc.common.security.CurrentUser;
import com.yunhefc.common.util.LikePattern;
import com.yunhefc.ledger.domain.LedgerEntry;
import com.yunhefc.ledger.domain.LedgerRules;
import com.yunhefc.ledger.domain.LedgerType;
import com.yunhefc.ledger.infrastructure.LedgerEntryEntity;
import com.yunhefc.ledger.dto.LedgerDtos.LedgerCategoryView;
import com.yunhefc.ledger.dto.LedgerDtos.LedgerEntryQuery;
import com.yunhefc.ledger.dto.LedgerDtos.LedgerFilterQuery;
import com.yunhefc.ledger.dto.LedgerDtos.LedgerEntryView;
import com.yunhefc.ledger.dto.LedgerDtos.LedgerSummaryQuery;
import com.yunhefc.ledger.dto.LedgerDtos.LedgerSummaryView;
import com.yunhefc.ledger.infrastructure.LedgerCategoryMapper;
import com.yunhefc.ledger.infrastructure.LedgerCategoryRow;
import com.yunhefc.ledger.infrastructure.LedgerSummaryRow;
import com.yunhefc.ledger.infrastructure.TeamLedgerEntryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

/**
 * C 端账本只读；LED-03/LED-07 复用同一查询，保证 B 端路径别名逐字段一致。
 */
@Service
@RequiredArgsConstructor
public class LedgerApplicationService {

    private final TeamLedgerEntryMapper entryMapper;
    private final LedgerCategoryMapper categoryMapper;
    private final LedgerViewAssembler assembler;

    @Transactional(readOnly = true)
    public PageResponse<LedgerEntryView> entries(LedgerEntryQuery query) {
        Long teamId = CurrentUser.principal().teamId();
        LedgerFilterQuery filter = query.filter();
        LedgerType type = LedgerRules.type(filter.getType());
        PageQuery page = PageQuery.of(query.page(), query.pageSize());
        String keyword = LedgerQueryParsing.keyword(query.keyword());
        Long categoryId = LedgerQueryParsing.positiveId(filter.getCategoryId(), "categoryId");
        Long playerId = LedgerQueryParsing.positiveId(filter.getPlayerId(), "playerId");
        Long activityId = LedgerQueryParsing.positiveId(filter.getActivityId(), "activityId");
        LocalDateTime from = LedgerQueryParsing.time(filter.getFrom(), "from");
        LocalDateTime to = LedgerQueryParsing.time(filter.getTo(), "to");
        LedgerQueryParsing.validateRange(from, to);

        LambdaQueryWrapper<LedgerEntryEntity> wrapper = new LambdaQueryWrapper<LedgerEntryEntity>()
                .eq(LedgerEntryEntity::getTeamId, teamId)
                .eq(type != null, LedgerEntryEntity::getType, type)
                .eq(categoryId != null, LedgerEntryEntity::getCategoryId, categoryId)
                .eq(playerId != null, LedgerEntryEntity::getPlayerId, playerId)
                .eq(activityId != null, LedgerEntryEntity::getActivityId, activityId)
                .ge(from != null, LedgerEntryEntity::getOccurredAt, from)
                .le(to != null, LedgerEntryEntity::getOccurredAt, to)
                .like(keyword != null, LedgerEntryEntity::getDescription,
                        keyword == null ? null : LikePattern.escape(keyword))
                .orderByDesc(LedgerEntryEntity::getOccurredAt)
                .orderByDesc(LedgerEntryEntity::getId);

        Page<LedgerEntryEntity> result = entryMapper.selectPage(
                new Page<>(page.page(), page.pageSize()), wrapper);
        return PageResponse.of(assembler.views(teamId,
                        result.getRecords().stream().map(LedgerEntryEntity::toDomain).toList()),
                result.getTotal(), page.page(), page.pageSize());
    }

    /**
     * 汇总 = 收入 − 支出，按区间聚合，不做期初余额。
     */
    @Transactional(readOnly = true)
    public LedgerSummaryView summary(LedgerSummaryQuery query) {
        Long teamId = CurrentUser.principal().teamId();
        LedgerFilterQuery filter = query.filter();
        LedgerType type = LedgerRules.type(filter.getType());
        Long categoryId = LedgerQueryParsing.positiveId(filter.getCategoryId(), "categoryId");
        Long playerId = LedgerQueryParsing.positiveId(filter.getPlayerId(), "playerId");
        Long activityId = LedgerQueryParsing.positiveId(filter.getActivityId(), "activityId");
        LocalDateTime from = LedgerQueryParsing.time(filter.getFrom(), "from");
        LocalDateTime to = LedgerQueryParsing.time(filter.getTo(), "to");
        LedgerQueryParsing.validateRange(from, to);

        LedgerSummaryRow row = entryMapper.selectSummary(teamId,
                LedgerType.INCOME.code(), LedgerType.EXPENSE.code(), from, to,
                type == null ? null : type.code(), categoryId, playerId, activityId);
        BigDecimal income = money(row == null ? null : row.getIncome());
        BigDecimal expense = money(row == null ? null : row.getExpense());
        return new LedgerSummaryView(income, expense, income.subtract(expense));
    }

    @Transactional(readOnly = true)
    public ItemsResponse<LedgerCategoryView> categories(Integer typeCode) {
        Long teamId = CurrentUser.principal().teamId();
        LedgerType type = LedgerRules.type(typeCode);
        return new ItemsResponse<>(categoryViews(teamId, type, false));
    }

    List<LedgerCategoryView> categoryViews(Long teamId, LedgerType type, boolean includeInactive) {
        List<LedgerCategoryRow> rows = categoryMapper.selectWithUsage(teamId,
                type == null ? null : type.code(), includeInactive);
        return rows.stream().map(assembler::categoryView).toList();
    }

    private static BigDecimal money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }
}
