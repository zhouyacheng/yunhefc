package com.yunhefc.ledger.application;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.yunhefc.audit.application.AuditRecorder;
import com.yunhefc.common.api.PageResponse;
import com.yunhefc.common.error.BusinessException;
import com.yunhefc.common.error.ErrorCode;
import com.yunhefc.common.security.CurrentUser;
import com.yunhefc.contracts.security.AuthPrincipal;
import com.yunhefc.ledger.domain.LedgerCategory;
import com.yunhefc.ledger.domain.LedgerEntry;
import com.yunhefc.ledger.domain.LedgerEntryKey;
import com.yunhefc.ledger.domain.LedgerRules;
import com.yunhefc.ledger.domain.LedgerType;
import com.yunhefc.ledger.dto.LedgerDtos.LedgerDeleteRequest;
import com.yunhefc.ledger.dto.LedgerDtos.LedgerDeletedResult;
import com.yunhefc.ledger.dto.LedgerDtos.LedgerEntryCreateRequest;
import com.yunhefc.ledger.dto.LedgerDtos.LedgerEntryQuery;
import com.yunhefc.ledger.dto.LedgerDtos.LedgerEntryUpdateRequest;
import com.yunhefc.ledger.dto.LedgerDtos.LedgerEntryView;
import com.yunhefc.ledger.dto.LedgerDtos.LedgerSummaryQuery;
import com.yunhefc.ledger.dto.LedgerDtos.LedgerSummaryView;
import com.yunhefc.ledger.infrastructure.TeamLedgerEntryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * B 端账本流水维护；LED-03/LED-07 直接复用 C 端查询。分类维护见
 * {@link LedgerCategoryAdminApplicationService}。
 */
@Service
@RequiredArgsConstructor
public class LedgerAdminApplicationService {

    private final LedgerApplicationService ledgerService;
    private final TeamLedgerEntryMapper entryMapper;
    private final LedgerAdminGuards guards;
    private final LedgerViewAssembler assembler;
    private final AuditRecorder auditRecorder;

    @Transactional(readOnly = true)
    public PageResponse<LedgerEntryView> entries(LedgerEntryQuery query) {
        return ledgerService.entries(query);
    }

    @Transactional(readOnly = true)
    public LedgerSummaryView summary(LedgerSummaryQuery query) {
        return ledgerService.summary(query);
    }

    @Transactional
    public LedgerEntryView create(LedgerEntryCreateRequest request) {
        AuthPrincipal principal = CurrentUser.principal();
        Long teamId = principal.teamId();
        LedgerType type = requireType(request.type());
        BigDecimal amount = LedgerRules.amount(request.amount());
        String description = LedgerRules.description(request.description());
        LedgerCategory category = guards.requireActiveCategory(teamId, request.categoryId());
        LedgerRules.requireDirectionMatch(type, category);
        if (request.playerId() != null) {
            guards.requireTeamPlayer(teamId, request.playerId());
        }
        if (request.activityId() != null) {
            guards.requireTeamActivity(teamId, request.activityId());
        }

        LedgerEntry entry = new LedgerEntry();
        entry.setTeamId(teamId);
        entry.setType(type);
        entry.setCategoryId(category.getId());
        entry.setAmount(amount);
        entry.setDescription(description);
        entry.setOccurredAt(request.occurredAt());
        entry.setPlayerId(request.playerId());
        entry.setActivityId(request.activityId());
        entry.setVersion(0);
        entry.setCreatedBy(principal.userId());
        entryMapper.insert(entry);

        auditRecorder.recordCurrent("LEDGER_CREATE", "LEDGER_ENTRY", entry.getId(),
                null, LedgerAuditSnapshot.of(entry));
        return assembler.view(teamId, entry);
    }

    @Transactional
    public LedgerEntryView update(Long entryId, LedgerEntryUpdateRequest request) {
        guards.rejectUnknownFields(request.getUnknownFields());
        Long teamId = CurrentUser.principal().teamId();
        LedgerEntryKey key = new LedgerEntryKey(entryId, teamId);
        LedgerEntry entry = entryMapper.selectByIdForUpdate(key);
        if (entry == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "流水不存在");
        }
        if (!request.getVersion().equals(entry.getVersion())) {
            throw guards.versionConflict(entry.getVersion());
        }

        LedgerType type = request.getType() == null ? entry.getType() : requireType(request.getType());
        Long categoryId = request.getCategoryId() == null
                ? entry.getCategoryId() : request.getCategoryId();
        boolean categoryAssigned = request.getCategoryId() != null
                && !request.getCategoryId().equals(entry.getCategoryId());
        boolean typeChanged = request.getType() != null && request.getType() != entry.getType().code();
        // 改分类才要求新分类启用；仅改方向按现有分类校验方向，停用分类的历史流水仍可编辑。
        if (categoryAssigned) {
            LedgerCategory category = guards.requireActiveCategory(teamId, categoryId);
            LedgerRules.requireDirectionMatch(type, category);
        } else if (typeChanged) {
            LedgerCategory category = guards.requireCategory(teamId, categoryId);
            LedgerRules.requireDirectionMatch(type, category);
        }
        BigDecimal amount = request.getAmount() == null
                ? entry.getAmount() : LedgerRules.amount(request.getAmount());
        String description = request.getDescription() == null
                ? entry.getDescription() : LedgerRules.description(request.getDescription());
        LocalDateTime occurredAt = request.getOccurredAt() == null
                ? entry.getOccurredAt() : request.getOccurredAt();

        Long playerId = entry.getPlayerId();
        if (request.hasPlayerId()) {
            playerId = request.getPlayerId();
            if (playerId != null) {
                guards.requireTeamPlayer(teamId, playerId);
            }
        }
        Long activityId = entry.getActivityId();
        if (request.hasActivityId()) {
            activityId = request.getActivityId();
            if (activityId != null) {
                guards.requireTeamActivity(teamId, activityId);
            }
        }

        Map<String, Object> before = LedgerAuditSnapshot.of(entry);
        int currentVersion = entry.getVersion();
        entry.setType(type);
        entry.setCategoryId(categoryId);
        entry.setAmount(amount);
        entry.setDescription(description);
        entry.setOccurredAt(occurredAt);
        entry.setPlayerId(playerId);
        entry.setActivityId(activityId);

        int updated = entryMapper.update(null, new LambdaUpdateWrapper<LedgerEntry>()
                .eq(LedgerEntry::getId, entryId)
                .eq(LedgerEntry::getTeamId, teamId)
                .eq(LedgerEntry::getVersion, currentVersion)
                .set(LedgerEntry::getType, type)
                .set(LedgerEntry::getCategoryId, categoryId)
                .set(LedgerEntry::getAmount, amount)
                .set(LedgerEntry::getDescription, description)
                .set(LedgerEntry::getOccurredAt, occurredAt)
                .set(LedgerEntry::getPlayerId, playerId)
                .set(LedgerEntry::getActivityId, activityId)
                .set(LedgerEntry::getVersion, currentVersion + 1));
        if (updated == 0) {
            throwNotFoundOrVersionConflict(key);
        }
        entry.setVersion(currentVersion + 1);
        auditRecorder.recordCurrent("LEDGER_UPDATE", "LEDGER_ENTRY", entryId,
                before, LedgerAuditSnapshot.of(entry));
        return assembler.view(teamId, entry);
    }

    @Transactional
    public LedgerDeletedResult delete(Long entryId, LedgerDeleteRequest request) {
        Long teamId = CurrentUser.principal().teamId();
        LedgerEntryKey key = new LedgerEntryKey(entryId, teamId);
        LedgerEntry entry = entryMapper.selectByIdForUpdate(key);
        if (entry == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "流水不存在");
        }
        if (!request.version().equals(entry.getVersion())) {
            throw guards.versionConflict(entry.getVersion());
        }
        Map<String, Object> before = LedgerAuditSnapshot.of(entry);
        if (entryMapper.logicalDelete(key, request.version()) == 0) {
            throwNotFoundOrVersionConflict(key);
        }
        auditRecorder.recordCurrent("LEDGER_DELETE", "LEDGER_ENTRY", entryId, before, null);
        return new LedgerDeletedResult(true);
    }

    private LedgerType requireType(Integer code) {
        LedgerType type = LedgerRules.type(code);
        if (type == null) {
            throw LedgerRules.invalid("type", "收支方向不能为空");
        }
        return type;
    }

    /**
     * 0 行更新后重新读取当前版本；行已消失时返回 404，否则返回 40901。
     */
    private void throwNotFoundOrVersionConflict(LedgerEntryKey key) {
        LedgerEntry current = entryMapper.selectByIdForUpdate(key);
        if (current == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "流水不存在");
        }
        throw guards.versionConflict(current.getVersion());
    }
}
