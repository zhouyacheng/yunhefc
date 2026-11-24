package com.yunhefc.ledger.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Getter;

import java.util.LinkedHashSet;
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
}
