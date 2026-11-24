package com.yunhefc.activity.dto;

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

public final class ActivityDtos {

    private ActivityDtos() {
    }

    public record ActivityQuery(
            Integer type,
            Integer status,
            String scope,
            String sort,
            long page,
            long pageSize) {
    }

    public record ActivityAdminQuery(
            Integer type,
            Integer status,
            String keyword,
            String startDate,
            String endDate,
            long page,
            long pageSize) {
    }

    public record ActivityCreateRequest(
            @NotBlank @Size(max = 128) String title,
            @NotNull @Min(0) @Max(2) Integer type,
            @NotNull LocalDateTime startAt,
            LocalDateTime endAt,
            @NotNull LocalDateTime signupDeadline,
            @Min(1) @Max(65535) Integer capacity,
            @DecimalMin("0") @Digits(integer = 8, fraction = 2) BigDecimal costPerPerson,
            @NotBlank @Size(max = 128) String location,
            @Size(max = 512) String locationImageKey,
            @Size(max = 1000) String description) {
    }

    /**
     * 部分更新：未出现的字段保持原值；可为空的字段显式传 null 表示清空。
     */
    @Getter
    @Setter
    public static class ActivityUpdateRequest {

        @Size(max = 128)
        private String title;

        @Min(0)
        @Max(2)
        private Integer type;

        private LocalDateTime startAt;

        private LocalDateTime endAt;

        private LocalDateTime signupDeadline;

        @Min(1)
        @Max(65535)
        private Integer capacity;

        @DecimalMin("0")
        @Digits(integer = 8, fraction = 2)
        private BigDecimal costPerPerson;

        @Size(max = 128)
        private String location;

        @Size(max = 512)
        private String locationImageKey;

        @Size(max = 1000)
        private String description;

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

        @JsonProperty("endAt")
        public void setEndAt(LocalDateTime endAt) {
            presentFields.add("endAt");
            this.endAt = endAt;
        }

        @JsonProperty("capacity")
        public void setCapacity(Integer capacity) {
            presentFields.add("capacity");
            this.capacity = capacity;
        }

        @JsonProperty("costPerPerson")
        public void setCostPerPerson(BigDecimal costPerPerson) {
            presentFields.add("costPerPerson");
            this.costPerPerson = costPerPerson;
        }

        @JsonProperty("locationImageKey")
        public void setLocationImageKey(String locationImageKey) {
            presentFields.add("locationImageKey");
            this.locationImageKey = locationImageKey;
        }

        @JsonProperty("description")
        public void setDescription(String description) {
            presentFields.add("description");
            this.description = description;
        }

        @JsonIgnore
        public boolean hasEndAt() {
            return presentFields.contains("endAt");
        }

        @JsonIgnore
        public boolean hasCapacity() {
            return presentFields.contains("capacity");
        }

        @JsonIgnore
        public boolean hasCostPerPerson() {
            return presentFields.contains("costPerPerson");
        }

        @JsonIgnore
        public boolean hasLocationImageKey() {
            return presentFields.contains("locationImageKey");
        }

        @JsonIgnore
        public boolean hasDescription() {
            return presentFields.contains("description");
        }

        @JsonAnySetter
        void addUnknownField(String field, JsonNode value) {
            unknownFields.add(field);
        }
    }

    public record ActivityStatusRequest(
            @NotNull @Min(0) @Max(3) Integer status,
            @NotNull @Min(0) Integer version,
            @Size(max = 255) String reason) {
    }

    public record ActivitySignupRequest(
            @NotNull Boolean withFamily,
            @Size(max = 255) String familyNote) {
    }

    public record ActivitySummary(
            Long id,
            String title,
            Integer type,
            String typeLabel,
            LocalDateTime startAt,
            LocalDateTime endAt,
            LocalDateTime signupDeadline,
            Integer capacity,
            BigDecimal costPerPerson,
            String location,
            String locationImageUrl,
            String description,
            Integer status,
            String statusLabel,
            int signupCount,
            boolean signupOpen,
            ActivitySignupView mySignup) {
    }

    public record ActivityAdmin(
            Long id,
            String title,
            Integer type,
            String typeLabel,
            LocalDateTime startAt,
            LocalDateTime endAt,
            LocalDateTime signupDeadline,
            Integer capacity,
            BigDecimal costPerPerson,
            String location,
            String locationImageKey,
            String locationImageUrl,
            String description,
            Integer status,
            String statusLabel,
            int signupCount,
            boolean signupOpen,
            Integer version,
            Long createdBy) {
    }

    public record ActivitySignupView(
            Long id,
            Long activityId,
            Long playerId,
            String playerName,
            Integer playerNo,
            String avatarUrl,
            boolean withFamily,
            String familyNote,
            Integer status,
            String statusLabel,
            LocalDateTime signedUpAt) {
    }

    public record ActivitySignupResponse(ActivitySignupView signup) {
    }

    public record ActivityCancelResult(boolean cancelled) {
    }

    public record ActivitySignupRemoveResult(boolean removed) {
    }

    public record ActivityImageUploadView(String objectKey, String signedUrl) {
    }
}
