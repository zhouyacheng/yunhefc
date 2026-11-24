package com.yunhefc.ledger.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.yunhefc.audit.application.AuditRecorder;
import com.yunhefc.common.api.ItemsResponse;
import com.yunhefc.common.error.BusinessException;
import com.yunhefc.common.error.ErrorCode;
import com.yunhefc.common.security.CurrentUser;
import com.yunhefc.ledger.domain.LedgerCategory;
import com.yunhefc.ledger.domain.LedgerRules;
import com.yunhefc.ledger.domain.LedgerType;
import com.yunhefc.ledger.dto.LedgerDtos.LedgerCategoryCreateRequest;
import com.yunhefc.ledger.dto.LedgerDtos.LedgerCategoryUpdateRequest;
import com.yunhefc.ledger.dto.LedgerDtos.LedgerCategoryView;
import com.yunhefc.ledger.infrastructure.LedgerCategoryEntity;
import com.yunhefc.ledger.infrastructure.LedgerCategoryMapper;
import com.yunhefc.ledger.infrastructure.LedgerEntryEntity;
import com.yunhefc.ledger.infrastructure.TeamLedgerEntryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * B 端账本分类维护；分类只停用不删除。
 */
@Service
@RequiredArgsConstructor
public class LedgerCategoryAdminApplicationService {

    private final LedgerApplicationService ledgerService;
    private final LedgerCategoryMapper categoryMapper;
    private final TeamLedgerEntryMapper entryMapper;
    private final LedgerAdminGuards guards;
    private final LedgerViewAssembler assembler;
    private final AuditRecorder auditRecorder;

    @Transactional(readOnly = true)
    public ItemsResponse<LedgerCategoryView> categories(Integer typeCode, Boolean includeInactive) {
        Long teamId = CurrentUser.principal().teamId();
        LedgerType type = LedgerRules.type(typeCode);
        // 05 §3.17 与票 04 均写明 LED-09「含停用分类」，故缺省包含；显式 false 才排除。
        return new ItemsResponse<>(ledgerService.categoryViews(teamId, type,
                includeInactive == null || includeInactive));
    }

    @Transactional
    public LedgerCategoryView createCategory(LedgerCategoryCreateRequest request) {
        Long teamId = CurrentUser.principal().teamId();
        String name = LedgerRules.categoryName(request.name());
        LedgerType direction = LedgerRules.categoryDirection(request.direction());
        ensureNameAvailable(teamId, name, null);

        LedgerCategoryEntity entity = new LedgerCategoryEntity();
        entity.setTeamId(teamId);
        entity.setName(name);
        entity.setDirection(direction);
        entity.setSortNo(request.sortNo() == null ? 0 : request.sortNo());
        entity.setIsActive(true);
        try {
            categoryMapper.insert(entity);
        } catch (DuplicateKeyException ex) {
            throw duplicateName();
        }

        LedgerCategory category = entity.toDomain();
        auditRecorder.recordCurrent("LEDGER_CATEGORY_CREATE", "LEDGER_CATEGORY", category.getId(),
                null, LedgerAuditSnapshot.of(category));
        return assembler.categoryView(category, 0);
    }

    @Transactional
    public LedgerCategoryView updateCategory(Long categoryId, LedgerCategoryUpdateRequest request) {
        guards.rejectUnknownFields(request.getUnknownFields());
        Long teamId = CurrentUser.principal().teamId();
        LedgerCategoryEntity current = categoryMapper.selectOne(
                new LambdaQueryWrapper<LedgerCategoryEntity>()
                        .eq(LedgerCategoryEntity::getId, categoryId)
                        .eq(LedgerCategoryEntity::getTeamId, teamId));
        if (current == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "分类不存在");
        }
        LedgerCategory category = current.toDomain();
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
            Long mismatched = entryMapper.selectCount(new LambdaQueryWrapper<LedgerEntryEntity>()
                    .eq(LedgerEntryEntity::getTeamId, teamId)
                    .eq(LedgerEntryEntity::getCategoryId, categoryId)
                    .ne(LedgerEntryEntity::getType, direction));
            if (mismatched != null && mismatched > 0) {
                throw LedgerRules.invalid("direction", "分类下存在方向不一致的流水，不能调整方向");
            }
        }

        try {
            int updated = categoryMapper.update(null, new LambdaUpdateWrapper<LedgerCategoryEntity>()
                    .eq(LedgerCategoryEntity::getId, categoryId)
                    .eq(LedgerCategoryEntity::getTeamId, teamId)
                    .set(LedgerCategoryEntity::getName, name)
                    .set(LedgerCategoryEntity::getDirection, direction)
                    .set(LedgerCategoryEntity::getSortNo, sortNo)
                    .set(LedgerCategoryEntity::getIsActive, isActive));
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
        return assembler.categoryView(category, countEntries(teamId, categoryId));
    }

    private void ensureNameAvailable(Long teamId, String name, Long excludeId) {
        Long count = categoryMapper.selectCount(new LambdaQueryWrapper<LedgerCategoryEntity>()
                .eq(LedgerCategoryEntity::getTeamId, teamId)
                .eq(LedgerCategoryEntity::getName, name)
                .ne(excludeId != null, LedgerCategoryEntity::getId, excludeId));
        if (count != null && count > 0) {
            throw duplicateName();
        }
    }

    private long countEntries(Long teamId, Long categoryId) {
        Long count = entryMapper.selectCount(new LambdaQueryWrapper<LedgerEntryEntity>()
                .eq(LedgerEntryEntity::getTeamId, teamId)
                .eq(LedgerEntryEntity::getCategoryId, categoryId));
        return count == null ? 0 : count;
    }

    private BusinessException duplicateName() {
        // 05 §3.17 将分类重名归入 40901。
        return new BusinessException(ErrorCode.VERSION_CONFLICT, "分类名称已存在");
    }
}
