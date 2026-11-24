package com.yunhefc.ledger.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.yunhefc.audit.application.AuditRecorder;
import com.yunhefc.common.api.ItemsResponse;
import com.yunhefc.common.api.PageResponse;
import com.yunhefc.common.error.BusinessException;
import com.yunhefc.common.error.ErrorCode;
import com.yunhefc.common.security.CurrentUser;
import com.yunhefc.contracts.activity.ActivityReadPort;
import com.yunhefc.contracts.player.PlayerReadPort;
import com.yunhefc.contracts.security.AuthPrincipal;
import com.yunhefc.ledger.domain.LedgerCategory;
import com.yunhefc.ledger.domain.LedgerEntry;
import com.yunhefc.ledger.domain.LedgerRules;
import com.yunhefc.ledger.domain.LedgerType;
import com.yunhefc.ledger.dto.LedgerDtos.LedgerCategoryCreateRequest;
import com.yunhefc.ledger.dto.LedgerDtos.LedgerCategoryUpdateRequest;
import com.yunhefc.ledger.dto.LedgerDtos.LedgerCategoryView;
import com.yunhefc.ledger.dto.LedgerDtos.LedgerDeleteRequest;
import com.yunhefc.ledger.dto.LedgerDtos.LedgerDeletedResult;
import com.yunhefc.ledger.dto.LedgerDtos.LedgerEntryCreateRequest;
import com.yunhefc.ledger.dto.LedgerDtos.LedgerEntryQuery;
import com.yunhefc.ledger.dto.LedgerDtos.LedgerEntryUpdateRequest;
import com.yunhefc.ledger.dto.LedgerDtos.LedgerEntryView;
import com.yunhefc.ledger.dto.LedgerDtos.LedgerSummaryQuery;
import com.yunhefc.ledger.dto.LedgerDtos.LedgerSummaryView;
import com.yunhefc.ledger.infrastructure.LedgerCategoryMapper;
import com.yunhefc.ledger.infrastructure.TeamLedgerEntryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * B 端账本：流水与分类维护；LED-03/LED-07 直接复用 C 端查询。
 */
@Service
@RequiredArgsConstructor
public class LedgerAdminApplicationService {

    private final LedgerApplicationService ledgerService;
    private final TeamLedgerEntryMapper entryMapper;
    private final LedgerCategoryMapper categoryMapper;
    private final PlayerReadPort playerReadPort;
    private final ActivityReadPort activityReadPort;
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
        LedgerCategory category = requireActiveCategory(teamId, request.categoryId());
        LedgerRules.requireDirectionMatch(type, category);
        if (request.playerId() != null) {
            requireTeamPlayer(teamId, request.playerId());
        }
        if (request.activityId() != null) {
            requireTeamActivity(teamId, request.activityId());
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
        rejectUnknownFields(request.getUnknownFields());
        Long teamId = CurrentUser.principal().teamId();
        LedgerEntry entry = entryMapper.selectByIdForUpdate(entryId, teamId);
        if (entry == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "流水不存在");
        }
        if (!request.getVersion().equals(entry.getVersion())) {
            throw versionConflict(entry.getVersion());
        }

        LedgerType type = request.getType() == null ? entry.getType() : requireType(request.getType());
        Long categoryId = request.getCategoryId() == null
                ? entry.getCategoryId() : request.getCategoryId();
        boolean categoryChanged = (request.getType() != null && request.getType() != entry.getType().code())
                || (request.getCategoryId() != null
                && !request.getCategoryId().equals(entry.getCategoryId()));
        // 分类未变更时允许继续编辑已停用分类的历史流水；变更分类才重新校验启用与方向。
        if (categoryChanged) {
            LedgerCategory category = requireActiveCategory(teamId, categoryId);
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
                requireTeamPlayer(teamId, playerId);
            }
        }
        Long activityId = entry.getActivityId();
        if (request.hasActivityId()) {
            activityId = request.getActivityId();
            if (activityId != null) {
                requireTeamActivity(teamId, activityId);
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
            throwNotFoundOrVersionConflict(entryId, teamId);
        }
        entry.setVersion(currentVersion + 1);
        auditRecorder.recordCurrent("LEDGER_UPDATE", "LEDGER_ENTRY", entryId,
                before, LedgerAuditSnapshot.of(entry));
        return assembler.view(teamId, entry);
    }

    @Transactional
    public LedgerDeletedResult delete(Long entryId, LedgerDeleteRequest request) {
        Long teamId = CurrentUser.principal().teamId();
        LedgerEntry entry = entryMapper.selectByIdForUpdate(entryId, teamId);
        if (entry == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "流水不存在");
        }
        if (!request.version().equals(entry.getVersion())) {
            throw versionConflict(entry.getVersion());
        }
        Map<String, Object> before = LedgerAuditSnapshot.of(entry);
        if (entryMapper.logicalDelete(entryId, teamId, request.version()) == 0) {
            throwNotFoundOrVersionConflict(entryId, teamId);
        }
        auditRecorder.recordCurrent("LEDGER_DELETE", "LEDGER_ENTRY", entryId, before, null);
        return new LedgerDeletedResult(true);
    }

    @Transactional(readOnly = true)
    public ItemsResponse<LedgerCategoryView> categories(Integer typeCode, Boolean includeInactive) {
        Long teamId = CurrentUser.principal().teamId();
        LedgerType type = LedgerRules.type(typeCode);
        return new ItemsResponse<>(ledgerService.categoryViews(teamId, type,
                Boolean.TRUE.equals(includeInactive)));
    }

    @Transactional
    public LedgerCategoryView createCategory(LedgerCategoryCreateRequest request) {
        Long teamId = CurrentUser.principal().teamId();
        String name = LedgerRules.categoryName(request.name());
        LedgerType direction = LedgerRules.categoryDirection(request.direction());
        ensureNameAvailable(teamId, name, null);

        LedgerCategory category = new LedgerCategory();
        category.setTeamId(teamId);
        category.setName(name);
        category.setDirection(direction);
        category.setSortNo(request.sortNo() == null ? 0 : request.sortNo());
        category.setIsActive(true);
        try {
            categoryMapper.insert(category);
        } catch (DuplicateKeyException ex) {
            throw duplicateName();
        }
        auditRecorder.recordCurrent("LEDGER_CATEGORY_CREATE", "LEDGER_CATEGORY", category.getId(),
                null, LedgerAuditSnapshot.of(category));
        return categoryView(category, 0);
    }

    @Transactional
    public LedgerCategoryView updateCategory(Long categoryId, LedgerCategoryUpdateRequest request) {
        rejectUnknownFields(request.getUnknownFields());
        Long teamId = CurrentUser.principal().teamId();
        LedgerCategory category = categoryMapper.selectOne(new LambdaQueryWrapper<LedgerCategory>()
                .eq(LedgerCategory::getId, categoryId)
                .eq(LedgerCategory::getTeamId, teamId));
        if (category == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "分类不存在");
        }
        Map<String, Object> before = LedgerAuditSnapshot.of(category);

        String name = request.getName() == null
                ? category.getName() : LedgerRules.categoryName(request.getName());
        if (!name.equals(category.getName())) {
            ensureNameAvailable(teamId, name, categoryId);
        }
        LedgerType direction = request.hasDirection()
                ? LedgerRules.categoryDirection(request.getDirection()) : category.getDirection();
        int sortNo = request.getSortNo() == null ? category.getSortNo() : request.getSortNo();
        boolean isActive = request.getIsActive() == null
                ? Boolean.TRUE.equals(category.getIsActive()) : request.getIsActive();
        // 分类改向不得让既有流水违反「方向必须与分类一致」：仅当存量流水全部匹配新方向时允许。
        if (direction != null && direction != category.getDirection()) {
            Long mismatched = entryMapper.selectCount(new LambdaQueryWrapper<LedgerEntry>()
                    .eq(LedgerEntry::getTeamId, teamId)
                    .eq(LedgerEntry::getCategoryId, categoryId)
                    .ne(LedgerEntry::getType, direction));
            if (mismatched != null && mismatched > 0) {
                throw LedgerRules.invalid("direction", "分类下存在方向不一致的流水，不能调整方向");
            }
        }

        try {
            int updated = categoryMapper.update(null, new LambdaUpdateWrapper<LedgerCategory>()
                    .eq(LedgerCategory::getId, categoryId)
                    .eq(LedgerCategory::getTeamId, teamId)
                    .set(LedgerCategory::getName, name)
                    .set(LedgerCategory::getDirection, direction)
                    .set(LedgerCategory::getSortNo, sortNo)
                    .set(LedgerCategory::getIsActive, isActive));
            if (updated == 0) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "分类不存在");
            }
        } catch (DuplicateKeyException ex) {
            throw duplicateName();
        }

        category.setName(name);
        category.setDirection(direction);
        category.setSortNo(sortNo);
        category.setIsActive(isActive);
        auditRecorder.recordCurrent("LEDGER_CATEGORY_UPDATE", "LEDGER_CATEGORY", categoryId,
                before, LedgerAuditSnapshot.of(category));
        return categoryView(category, countEntries(teamId, categoryId));
    }

    private LedgerType requireType(Integer code) {
        LedgerType type = LedgerRules.type(code);
        if (type == null) {
            throw LedgerRules.invalid("type", "收支方向不能为空");
        }
        return type;
    }

    private LedgerCategory requireActiveCategory(Long teamId, Long categoryId) {
        LedgerCategory category = categoryMapper.selectOne(new LambdaQueryWrapper<LedgerCategory>()
                .eq(LedgerCategory::getId, categoryId)
                .eq(LedgerCategory::getTeamId, teamId));
        if (category == null) {
            throw LedgerRules.invalid("categoryId", "分类不存在或不属于本队");
        }
        if (!Boolean.TRUE.equals(category.getIsActive())) {
            throw LedgerRules.invalid("categoryId", "分类已停用");
        }
        return category;
    }

    private void requireTeamPlayer(Long teamId, Long playerId) {
        playerReadPort.findById(playerId)
                .filter(player -> teamId.equals(player.teamId()))
                .orElseThrow(() -> LedgerRules.invalid("playerId", "归属球员不存在或不属于本队"));
    }

    private void requireTeamActivity(Long teamId, Long activityId) {
        activityReadPort.findById(teamId, activityId)
                .orElseThrow(() -> LedgerRules.invalid("activityId", "关联活动不存在或不属于本队"));
    }

    private void ensureNameAvailable(Long teamId, String name, Long excludeId) {
        Long count = categoryMapper.selectCount(new LambdaQueryWrapper<LedgerCategory>()
                .eq(LedgerCategory::getTeamId, teamId)
                .eq(LedgerCategory::getName, name)
                .ne(excludeId != null, LedgerCategory::getId, excludeId));
        if (count != null && count > 0) {
            throw duplicateName();
        }
    }

    private long countEntries(Long teamId, Long categoryId) {
        Long count = entryMapper.selectCount(new LambdaQueryWrapper<LedgerEntry>()
                .eq(LedgerEntry::getTeamId, teamId)
                .eq(LedgerEntry::getCategoryId, categoryId));
        return count == null ? 0 : count;
    }

    private LedgerCategoryView categoryView(LedgerCategory category, long entryCount) {
        LedgerType direction = category.getDirection();
        return new LedgerCategoryView(
                category.getId(),
                category.getName(),
                direction == null ? null : direction.code(),
                direction == null ? null : direction.label(),
                category.getSortNo(),
                Boolean.TRUE.equals(category.getIsActive()),
                entryCount);
    }

    /**
     * 0 行更新后重新读取当前版本；行已消失时返回 404，否则返回 40901。
     */
    private void throwNotFoundOrVersionConflict(Long entryId, Long teamId) {
        LedgerEntry current = entryMapper.selectByIdForUpdate(entryId, teamId);
        if (current == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "流水不存在");
        }
        throw versionConflict(current.getVersion());
    }

    private BusinessException versionConflict(int currentVersion) {
        return new BusinessException(ErrorCode.VERSION_CONFLICT, "流水已被他人修改，请刷新后重试",
                Map.of("currentVersion", currentVersion));
    }

    private BusinessException duplicateName() {
        // 05 §3.17 将分类重名归入 40901。
        return new BusinessException(ErrorCode.VERSION_CONFLICT, "分类名称已存在");
    }

    private void rejectUnknownFields(Set<String> unknownFields) {
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
