package com.yunhefc.ledger.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yunhefc.common.api.ItemsResponse;
import com.yunhefc.common.api.PageQuery;
import com.yunhefc.common.api.PageResponse;
import com.yunhefc.common.security.CurrentUser;
import com.yunhefc.common.util.LikePattern;
import com.yunhefc.ledger.domain.LedgerEntry;
import com.yunhefc.ledger.domain.LedgerPeriod;
import com.yunhefc.ledger.domain.LedgerRules;
import com.yunhefc.ledger.domain.LedgerType;
import com.yunhefc.ledger.dto.LedgerDtos.LedgerCategoryView;
import com.yunhefc.ledger.dto.LedgerDtos.LedgerDailyTotalView;
import com.yunhefc.ledger.dto.LedgerDtos.LedgerEntryQuery;
import com.yunhefc.ledger.dto.LedgerDtos.LedgerEntryView;
import com.yunhefc.ledger.dto.LedgerDtos.LedgerFilterQuery;
import com.yunhefc.ledger.dto.LedgerDtos.LedgerStatisticsQuery;
import com.yunhefc.ledger.dto.LedgerDtos.LedgerStatisticsView;
import com.yunhefc.ledger.dto.LedgerDtos.LedgerSummaryQuery;
import com.yunhefc.ledger.dto.LedgerDtos.LedgerSummaryView;
import com.yunhefc.ledger.dto.LedgerDtos.LedgerTrendPointView;
import com.yunhefc.ledger.infrastructure.LedgerCategoryMapper;
import com.yunhefc.ledger.infrastructure.LedgerCategoryRow;
import com.yunhefc.ledger.infrastructure.LedgerDailyTotalRow;
import com.yunhefc.ledger.infrastructure.LedgerSummaryRow;
import com.yunhefc.ledger.infrastructure.LedgerTotalsRow;
import com.yunhefc.ledger.infrastructure.LedgerTrendRow;
import com.yunhefc.ledger.infrastructure.TeamLedgerEntryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
        ParsedFilter filter = ParsedFilter.of(query.filter(), query.keyword());
        PageQuery page = PageQuery.of(query.page(), query.pageSize());

        LambdaQueryWrapper<LedgerEntry> wrapper = new LambdaQueryWrapper<LedgerEntry>()
                .eq(LedgerEntry::getTeamId, teamId)
                .eq(filter.type() != null, LedgerEntry::getType, filter.type())
                .eq(filter.categoryId() != null, LedgerEntry::getCategoryId, filter.categoryId())
                .eq(filter.playerId() != null, LedgerEntry::getPlayerId, filter.playerId())
                .eq(filter.activityId() != null, LedgerEntry::getActivityId, filter.activityId())
                .ge(filter.from() != null, LedgerEntry::getOccurredAt, filter.from())
                .le(filter.to() != null, LedgerEntry::getOccurredAt, filter.to())
                .like(filter.keyword() != null, LedgerEntry::getDescription,
                        filter.keyword() == null ? null : LikePattern.escape(filter.keyword()))
                .orderByDesc(LedgerEntry::getOccurredAt)
                .orderByDesc(LedgerEntry::getId);

        Page<LedgerEntry> result = entryMapper.selectPage(
                new Page<>(page.page(), page.pageSize()), wrapper);
        return PageResponse.of(assembler.views(teamId, result.getRecords()),
                result.getTotal(), page.page(), page.pageSize());
    }

    /**
     * 汇总 = 收入 − 支出，按区间聚合，不做期初余额。
     */
    @Transactional(readOnly = true)
    public LedgerSummaryView summary(LedgerSummaryQuery query) {
        Long teamId = CurrentUser.principal().teamId();
        ParsedFilter filter = ParsedFilter.of(query.filter(), null);

        LedgerSummaryRow row = entryMapper.selectSummary(teamId,
                LedgerType.INCOME.code(), LedgerType.EXPENSE.code(),
                filter.from(), filter.to(),
                filter.typeCode(), filter.categoryId(), filter.playerId(), filter.activityId());
        BigDecimal income = money(row == null ? null : row.getIncome());
        BigDecimal expense = money(row == null ? null : row.getExpense());
        return new LedgerSummaryView(income, expense, income.subtract(expense));
    }

    /**
     * LED-13：按自然日聚合，筛选口径与列表一致（含关键字）。
     */
    @Transactional(readOnly = true)
    public ItemsResponse<LedgerDailyTotalView> dailyTotals(LedgerFilterQuery query, String keyword) {
        Long teamId = CurrentUser.principal().teamId();
        ParsedFilter filter = ParsedFilter.of(query, keyword);
        List<LedgerDailyTotalRow> rows = entryMapper.selectDailyTotals(teamId,
                LedgerType.INCOME.code(), LedgerType.EXPENSE.code(),
                filter.from(), filter.to(), filter.typeCode(),
                filter.categoryId(), filter.playerId(), filter.activityId(),
                filter.keyword() == null ? null : LikePattern.escape(filter.keyword()));
        return new ItemsResponse<>(rows.stream()
                .map(row -> new LedgerDailyTotalView(row.getEntryDate(),
                        money(row.getIncome()), money(row.getExpense())))
                .toList());
    }

    /**
     * LED-14：周期收支合计与笔数、上一周期对比与趋势分桶。
     */
    @Transactional(readOnly = true)
    public LedgerStatisticsView statistics(LedgerStatisticsQuery query) {
        Long teamId = CurrentUser.principal().teamId();
        // period=custom 忽略 anchor，不因无关参数校验失败。
        LocalDate anchor = LedgerPeriod.isCustom(query.getPeriod())
                ? null
                : LedgerQueryParsing.date(query.getAnchor(), "anchor");
        LocalDate from = LedgerQueryParsing.date(query.getFrom(), "from");
        LocalDate to = LedgerQueryParsing.date(query.getTo(), "to");
        LedgerPeriod period = LedgerPeriod.of(query.getPeriod(), anchor, from, to);

        LedgerTotalsRow current = totals(teamId, period.from(), period.to());
        LedgerTotalsRow previous = totals(teamId, period.previousFrom(), period.previousTo());
        List<LedgerTrendRow> rows = trendRows(teamId, period);

        BigDecimal income = money(current.getIncome());
        BigDecimal expense = money(current.getExpense());
        return new LedgerStatisticsView(
                period.period(), period.from(), period.to(), period.previousFrom(), period.previousTo(),
                income, expense, income.subtract(expense),
                count(current.getIncomeCount()), count(current.getExpenseCount()),
                money(previous.getIncome()), money(previous.getExpense()),
                money(previous.getIncome()).subtract(money(previous.getExpense())),
                period.trendGranularity(), fillTrend(period, rows));
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

    private LedgerTotalsRow totals(Long teamId, LocalDate from, LocalDate to) {
        return entryMapper.selectTotals(teamId, LedgerType.INCOME.code(), LedgerType.EXPENSE.code(),
                from.atStartOfDay(), to.plusDays(1).atStartOfDay());
    }

    private List<LedgerTrendRow> trendRows(Long teamId, LedgerPeriod period) {
        LocalDateTime from = period.from().atStartOfDay();
        LocalDateTime to = period.to().plusDays(1).atStartOfDay();
        return LedgerPeriod.GRANULARITY_DAY.equals(period.trendGranularity())
                ? entryMapper.selectDailyTrend(teamId, LedgerType.INCOME.code(),
                        LedgerType.EXPENSE.code(), from, to)
                : entryMapper.selectMonthlyTrend(teamId, LedgerType.INCOME.code(),
                        LedgerType.EXPENSE.code(), from, to);
    }

    /** 趋势按日/按月零填充，保证前端拿到连续的桶。 */
    private static List<LedgerTrendPointView> fillTrend(LedgerPeriod period,
                                                        List<LedgerTrendRow> rows) {
        Map<LocalDate, LedgerTrendRow> byStart = new HashMap<>();
        for (LedgerTrendRow row : rows) {
            byStart.put(row.getBucketStart(), row);
        }
        List<LedgerTrendPointView> points = new ArrayList<>();
        if (LedgerPeriod.GRANULARITY_DAY.equals(period.trendGranularity())) {
            for (LocalDate day = period.from(); !day.isAfter(period.to()); day = day.plusDays(1)) {
                points.add(trendPoint(day, byStart.get(day)));
            }
            return points;
        }
        YearMonth end = YearMonth.from(period.to());
        for (YearMonth month = YearMonth.from(period.from()); !month.isAfter(end);
             month = month.plusMonths(1)) {
            LocalDate start = month.atDay(1);
            points.add(trendPoint(start, byStart.get(start)));
        }
        return points;
    }

    private static LedgerTrendPointView trendPoint(LocalDate start, LedgerTrendRow row) {
        return new LedgerTrendPointView(start,
                money(row == null ? null : row.getIncome()),
                money(row == null ? null : row.getExpense()));
    }

    private static long count(Long value) {
        return value == null ? 0L : value;
    }

    private static BigDecimal money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }

    /** LED-01/02/13 共用的筛选条件与校验结果。 */
    private record ParsedFilter(
            LedgerType type,
            Long categoryId,
            Long playerId,
            Long activityId,
            LocalDateTime from,
            LocalDateTime to,
            String keyword) {

        static ParsedFilter of(LedgerFilterQuery filter, String keyword) {
            LedgerType type = LedgerRules.type(filter.getType());
            LocalDateTime from = LedgerQueryParsing.time(filter.getFrom(), "from");
            LocalDateTime to = LedgerQueryParsing.time(filter.getTo(), "to");
            LedgerQueryParsing.validateRange(from, to);
            return new ParsedFilter(
                    type,
                    LedgerQueryParsing.positiveId(filter.getCategoryId(), "categoryId"),
                    LedgerQueryParsing.positiveId(filter.getPlayerId(), "playerId"),
                    LedgerQueryParsing.positiveId(filter.getActivityId(), "activityId"),
                    from,
                    to,
                    LedgerQueryParsing.keyword(keyword));
        }

        Integer typeCode() {
            return type == null ? null : type.code();
        }
    }
}
