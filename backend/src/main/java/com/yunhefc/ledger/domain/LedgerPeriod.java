package com.yunhefc.ledger.domain;

import com.yunhefc.common.error.BusinessException;
import com.yunhefc.common.error.ErrorCode;
import com.yunhefc.common.time.ShanghaiTime;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;

/**
 * LED-14 统计周期：自然周（周一至周日）/自然月/自然年，以及自定义闭区间。
 * 上一周期用于环比；趋势粒度按区间长度取日或月。
 */
public record LedgerPeriod(
        String period,
        LocalDate from,
        LocalDate to,
        LocalDate previousFrom,
        LocalDate previousTo,
        String trendGranularity) {

    public static final String WEEK = "week";
    public static final String MONTH = "month";
    public static final String YEAR = "year";
    public static final String CUSTOM = "custom";

    public static final String GRANULARITY_DAY = "day";
    public static final String GRANULARITY_MONTH = "month";

    /** 自定义区间上限 10 年，避免趋势分桶失控。 */
    private static final int MAX_CUSTOM_DAYS = 3660;
    /** 超过 62 天的区间按月分桶。 */
    private static final int DAY_TREND_MAX_DAYS = 62;

    /** 缺省为自然月；其余按小写匹配，非法值留给 {@link #of} 统一报错。 */
    public static String normalize(String period) {
        return period == null || period.isBlank() ? MONTH : period.strip().toLowerCase();
    }

    public static boolean isCustom(String period) {
        return CUSTOM.equals(normalize(period));
    }

    public static LedgerPeriod of(String period, LocalDate anchor, LocalDate from, LocalDate to) {
        String normalized = normalize(period);
        LocalDate base = anchor == null ? LocalDate.now(ShanghaiTime.ZONE) : anchor;
        return switch (normalized) {
            case WEEK -> week(base);
            case MONTH -> month(base);
            case YEAR -> year(base);
            case CUSTOM -> custom(from, to);
            default -> throw invalid("period 必须为 week/month/year/custom");
        };
    }

    private static LedgerPeriod week(LocalDate base) {
        LocalDate start = base.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        return new LedgerPeriod(WEEK, start, start.plusDays(6),
                start.minusDays(7), start.minusDays(1), GRANULARITY_DAY);
    }

    private static LedgerPeriod month(LocalDate base) {
        LocalDate start = base.withDayOfMonth(1);
        LocalDate end = start.plusMonths(1).minusDays(1);
        return new LedgerPeriod(MONTH, start, end,
                start.minusMonths(1), start.minusDays(1), GRANULARITY_DAY);
    }

    private static LedgerPeriod year(LocalDate base) {
        LocalDate start = base.withDayOfYear(1);
        LocalDate end = base.withDayOfYear(base.lengthOfYear());
        return new LedgerPeriod(YEAR, start, end,
                start.minusYears(1), start.minusDays(1), GRANULARITY_MONTH);
    }

    private static LedgerPeriod custom(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw invalid("自定义时间需要开始与结束日期");
        }
        if (from.isAfter(to)) {
            throw invalid("开始日期不能晚于结束日期");
        }
        long days = ChronoUnit.DAYS.between(from, to) + 1;
        if (days > MAX_CUSTOM_DAYS) {
            throw invalid("自定义时间跨度不能超过 10 年");
        }
        String granularity = days <= DAY_TREND_MAX_DAYS ? GRANULARITY_DAY : GRANULARITY_MONTH;
        return new LedgerPeriod(CUSTOM, from, to, from.minusDays(days), to.minusDays(days),
                granularity);
    }

    private static BusinessException invalid(String message) {
        return new BusinessException(ErrorCode.VALIDATION_FAILED, message);
    }
}
