package com.yunhefc.ledger.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
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
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeSet;

public final class LedgerDtos {

    private LedgerDtos() {
    }

    public record LedgerEntryQuery(
            Integer type,
            Long categoryId,
            Long playerId,
            Long activityId,
            String from,
            String to,
            String keyword,
            long page,
            long pageSize) {
    }

    public record LedgerSummaryQuery(
            Integer type,
            Long categoryId,
            Long playerId,
            Long activityId,
            String from,
            String to) {
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
            @NotBlank @Size(max = 255) String description,
            @NotNull LocalDateTime occurredAt,
            @Min(1) Long playerId,
            @Min(1) Long activityId) {
    }

    /**
     * 部分更新：未出现的字段保持原值；playerId/activityId 显式传 null 表示清空。
     */
    @Getter
    @Setter
    public static class LedgerEntryUpdateRequest {

        @Min(0)
        @Max(1)
        private Integer type;

        @Min(1)
        private Long categoryId;

        @DecimalMin(value = "0", inclusive = false)
        @Digits(integer = 8, fraction = 2)
        private BigDecimal amount;

        @Size(max = 255)
        private String description;

        private LocalDateTime occurredAt;

        @Min(1)
        private Long playerId;

        @Min(1)
        private Long activityId;

        @NotNull
        @Min(0)
        private Integer version;

        @JsonIgnore
        private final Set<String> presentFields = new LinkedHashSet<>();

        @JsonIgnore
        private final Set<String> unknownFields = new LinkedHashSet<>();

        /**
         * 只读幂等指纹：让请求哈希区分「显式 null」与字段缺失及未知字段，同时不暴露可写属性。
         */
        @JsonProperty(value = "_presence", access = JsonProperty.Access.READ_ONLY)
        public String presenceFingerprint() {
            return new TreeSet<>(presentFields) + "|" + new TreeSet<>(unknownFields);
        }

        @JsonProperty("playerId")
        public void setPlayerId(Long playerId) {
            presentFields.add("playerId");
            this.playerId = playerId;
        }

        @JsonProperty("activityId")
        public void setActivityId(Long activityId) {
            presentFields.add("activityId");
            this.activityId = activityId;
        }

        @JsonIgnore
        public boolean hasPlayerId() {
            return presentFields.contains("playerId");
        }

        @JsonIgnore
        public boolean hasActivityId() {
            return presentFields.contains("activityId");
        }

        @JsonAnySetter
        void addUnknownField(String field, JsonNode value) {
            unknownFields.add(field);
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
    public static class LedgerCategoryUpdateRequest {

        @Size(max = 32)
        private String name;

        @Min(0)
        @Max(1)
        private Integer direction;

        @Min(0)
        @Max(65535)
        private Integer sortNo;

        private Boolean isActive;

        @JsonIgnore
        private final Set<String> presentFields = new LinkedHashSet<>();

        @JsonIgnore
        private final Set<String> unknownFields = new LinkedHashSet<>();

        @JsonProperty(value = "_presence", access = JsonProperty.Access.READ_ONLY)
        public String presenceFingerprint() {
            return new TreeSet<>(presentFields) + "|" + new TreeSet<>(unknownFields);
        }

        @JsonProperty("direction")
        public void setDirection(Integer direction) {
            presentFields.add("direction");
            this.direction = direction;
        }

        @JsonIgnore
        public boolean hasDirection() {
            return presentFields.contains("direction");
        }

        @JsonAnySetter
        void addUnknownField(String field, JsonNode value) {
            unknownFields.add(field);
        }
    }

    public record LedgerDeletedResult(boolean deleted) {
    }
}
