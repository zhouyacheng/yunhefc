package com.yunhefc.common.api;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.yunhefc.common.error.BusinessException;
import com.yunhefc.common.error.ErrorCode;
import lombok.Getter;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * 部分更新请求的 presence 跟踪：区分显式 null 与字段缺失，并让未知字段参与幂等哈希。
 */
@Getter
public abstract class PresenceTrackedRequest {

    @JsonIgnore
    private final Set<String> presentFields = new LinkedHashSet<>();

    @JsonIgnore
    private final Set<String> unknownFields = new LinkedHashSet<>();

    @JsonProperty(value = "_presence", access = JsonProperty.Access.READ_ONLY)
    public String presenceFingerprint() {
        return new TreeSet<>(presentFields) + "|" + new TreeSet<>(unknownFields);
    }

    protected void markPresent(String field) {
        presentFields.add(field);
    }

    @JsonAnySetter
    protected void addUnknownField(String field, JsonNode value) {
        unknownFields.add(field);
    }

    /**
     * 未知字段一律视为不可修改字段，返回 40001。
     */
    @JsonIgnore
    public void rejectUnknownFields() {
        if (unknownFields.isEmpty()) {
            return;
        }
        Map<String, String> details = new LinkedHashMap<>();
        for (String field : unknownFields) {
            details.put(field, "不可修改字段");
        }
        throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                ErrorCode.VALIDATION_FAILED.message(), Map.of("details", details));
    }
}
