package com.yunhefc.common.idempotency;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yunhefc.common.api.ApiResponse;
import com.yunhefc.common.error.BusinessException;
import com.yunhefc.common.error.ErrorCode;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.concurrent.Callable;

@Service
public class IdempotencyService {

    private static final int IN_PROGRESS_GRACE_SECONDS = 60;

    private final IdempotencyKeyMapper mapper;
    private final ObjectMapper objectMapper;

    public IdempotencyService(IdempotencyKeyMapper mapper, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    public ApiResponse<?> execute(Long userId, String endpoint, String idemKey, String requestHash,
                                  Callable<ApiResponse<?>> action) throws Exception {
        IdempotencyKey existing = mapper.find(userId, endpoint, idemKey);
        if (existing != null) {
            ApiResponse<?> replayed = replayOrReclaim(existing, requestHash);
            if (replayed != null) {
                return replayed;
            }
        }

        IdempotencyKey row = new IdempotencyKey();
        row.setUserId(userId);
        row.setEndpoint(endpoint);
        row.setIdemKey(idemKey);
        row.setRequestHash(requestHash);
        row.setCreatedAt(LocalDateTime.now());
        row.setExpiresAt(LocalDateTime.now().plusHours(24));
        try {
            mapper.insert(row);
        } catch (DuplicateKeyException ex) {
            IdempotencyKey winner = mapper.find(userId, endpoint, idemKey);
            if (winner == null) {
                throw new BusinessException(ErrorCode.IDEMPOTENCY_IN_PROGRESS);
            }
            ApiResponse<?> replayed = replayOrReclaim(winner, requestHash);
            if (replayed == null) {
                throw new BusinessException(ErrorCode.IDEMPOTENCY_IN_PROGRESS);
            }
            return replayed;
        }

        try {
            ApiResponse<?> result = action.call();
            row.setResponseStatus(200);
            row.setResponseBody(objectMapper.writeValueAsString(result.data()));
            mapper.updateById(row);
            return result;
        } catch (Exception ex) {
            mapper.deletePhysical(row.getId());
            throw ex;
        }
    }

    private ApiResponse<?> replayOrReclaim(IdempotencyKey row, String requestHash) throws Exception {
        if (!row.getRequestHash().equals(requestHash)) {
            throw new BusinessException(ErrorCode.IDEMPOTENCY_KEY_REUSED);
        }
        if (row.getResponseBody() != null) {
            JsonNode data = objectMapper.readTree(row.getResponseBody());
            return ApiResponse.ok(data == null || data.isNull() ? null : data);
        }
        LocalDateTime staleBefore = LocalDateTime.now().minusSeconds(IN_PROGRESS_GRACE_SECONDS);
        if (mapper.deleteIfStale(row.getId(), staleBefore) == 1) {
            // null 表示占位已回收，调用方应重新插入占位并执行
            return null;
        }
        throw new BusinessException(ErrorCode.IDEMPOTENCY_IN_PROGRESS);
    }
}
