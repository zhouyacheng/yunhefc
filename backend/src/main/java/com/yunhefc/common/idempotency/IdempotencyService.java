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
            return replay(existing, requestHash);
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
            return replay(winner, requestHash);
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

    private ApiResponse<?> replay(IdempotencyKey row, String requestHash) throws Exception {
        if (!row.getRequestHash().equals(requestHash)) {
            throw new BusinessException(ErrorCode.IDEMPOTENCY_KEY_REUSED);
        }
        if (row.getResponseBody() == null) {
            if (row.getCreatedAt().plusSeconds(IN_PROGRESS_GRACE_SECONDS).isBefore(LocalDateTime.now())) {
                throw new BusinessException(ErrorCode.IDEMPOTENCY_IN_PROGRESS);
            }
            throw new BusinessException(ErrorCode.IDEMPOTENCY_IN_PROGRESS);
        }
        JsonNode data = objectMapper.readTree(row.getResponseBody());
        return ApiResponse.ok(data == null || data.isNull() ? null : data);
    }
}
