package com.yunhefc.player.domain;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yunhefc.common.error.BusinessException;
import com.yunhefc.common.error.ErrorCode;

import java.util.List;
import java.util.Objects;

public final class SecondaryPositions {

    private SecondaryPositions() {
    }

    /**
     * 解析副位置 JSON；脏数据不阻断公开读取，仅保留合法位置编码。
     */
    public static List<Integer> parse(ObjectMapper objectMapper, String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<Integer> positions = objectMapper.readValue(json, new TypeReference<List<Integer>>() {
            });
            if (positions == null) {
                return List.of();
            }
            return positions.stream()
                    .filter(Objects::nonNull)
                    .filter(code -> code >= 0 && code <= 3)
                    .toList();
        } catch (JsonProcessingException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "副位置数据格式错误");
        }
    }
}
