package com.yunhefc.ledger.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.yunhefc.common.api.PresenceTrackedRequest;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class LedgerDtos {

    private LedgerDtos() {
    }

    /**
     * LED-01/02/03/07 共用的筛选条件；由 Spring MVC 从查询参数绑定。
     */
    @Getter
    @Setter
    public static class LedgerFilterQuery {

        private Integer type;
        private Long categoryId;
        private Long playerId;
        private Long activityId;
        private String from;
        private String to;
    }

    public record LedgerEntryQuery(
            LedgerFilterQuery filter,
            String keyword,
            long page,
            long pageSize) {
    }

    public record LedgerSummaryQuery(LedgerFilterQuery filter) {
    }

    public record LedgerEntryView(
            Long id,
            Integer type,
            String typeLabel,
            Long categoryId,
            String categoryName,
            Integer categoryDirection,
            BigDecimal amount,
            String description,
            LocalDateTime occurredAt,
            Long playerId,
            String playerName,
            Long activityId,
            String activityTitle,
            Integer version) {
    }

    public record LedgerSummaryView(
            BigDecimal income,
            BigDecimal expense,
            BigDecimal balance) {

    }

    /** LED-13：按自然日聚合的收支小计。 */
    public record LedgerDailyTotalView(
            LocalDate date,
            BigDecimal income,
            BigDecimal expense) {
    }

    /** LED-14 查询参数；period=custom 时使用 from/to（YYYY-MM-DD，闭区间）。 */
    @Getter
    @Setter
    public static class LedgerStatisticsQuery {

        private String period;
        private String anchor;
        private String from;
        private String to;
    }

    public record LedgerTrendPointView(
            LocalDate start,
            BigDecimal income,
            BigDecimal expense) {
    }

    public record LedgerStatisticsView(
            String period,
            LocalDate from,
            LocalDate to,
            LocalDate previousFrom,
            LocalDate previousTo,
            BigDecimal income,
            BigDecimal expense,
            BigDecimal balance,
            long incomeCount,
            long expenseCount,
            BigDecimal previousIncome,
            BigDecimal previousExpense,
            BigDecimal previousBalance,
            String trendGranularity,
            List<LedgerTrendPointView> trend) {
    }

    public record LedgerCategoryView(
            Long id,
            String name,
            Integer direction,
            String directionLabel,
            Integer sortNo,
            boolean isActive,
            long entryCount) {
    }

    public record LedgerEntryCreateRequest(
            @NotNull @Min(0) @Max(1) Integer type,
            @NotNull @Min(1) Long categoryId,
            @NotNull @DecimalMin(value = "0", inclusive = false)
            @Digits(integer = 8, fraction = 2) BigDecimal amount,
            String description,
            @NotNull LocalDateTime occurredAt,
            @Min(1) Long playerId,
            @Min(1) Long activityId) {
    }

    /**
     * 部分更新：未出现的字段保持原值；playerId/activityId 显式传 null 表示清空。
     */
    @Getter
    @Setter
    public static class LedgerEntryUpdateRequest extends PresenceTrackedRequest {

        @Min(0)
        @Max(1)
        private Integer type;

        @Min(1)
        private Long categoryId;

        @DecimalMin(value = "0", inclusive = false)
        @Digits(integer = 8, fraction = 2)
        private BigDecimal amount;

        private String description;

        private LocalDateTime occurredAt;

        @Min(1)
        private Long playerId;

        @Min(1)
        private Long activityId;

        @NotNull
        @Min(0)
        private Integer version;

        @JsonProperty("playerId")
        public void setPlayerId(Long playerId) {
            markPresent("playerId");
            this.playerId = playerId;
        }

        @JsonProperty("activityId")
        public void setActivityId(Long activityId) {
            markPresent("activityId");
            this.activityId = activityId;
        }

        @JsonIgnore
        public boolean hasPlayerId() {
            return getPresentFields().contains("playerId");
        }

        @JsonIgnore
        public boolean hasActivityId() {
            return getPresentFields().contains("activityId");
        }
    }

    public record LedgerDeleteRequest(
            @NotNull @Min(0) Integer version) {
    }

    public record LedgerCategoryCreateRequest(
            @NotBlank @Size(max = 32) String name,
            @Min(0) @Max(1) Integer direction,
            @Min(0) @Max(65535) Integer sortNo) {
    }

    /**
     * 部分更新：direction 显式传 null 表示改为通用分类。
     */
    @Getter
    @Setter
    public static class LedgerCategoryUpdateRequest extends PresenceTrackedRequest {

        @Size(max = 32)
        private String name;

        @Min(0)
        @Max(1)
        private Integer direction;

        @Min(0)
        @Max(65535)
        private Integer sortNo;

        private Boolean isActive;

        @JsonProperty("direction")
        public void setDirection(Integer direction) {
            markPresent("direction");
            this.direction = direction;
        }

        @JsonIgnore
        public boolean hasDirection() {
            return getPresentFields().contains("direction");
        }
    }

    public record LedgerDeletedResult(boolean deleted) {
    }
}
