package com.yunhefc.lineup.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yunhefc.common.error.BusinessException;
import com.yunhefc.common.error.ErrorCode;
import com.yunhefc.common.security.CurrentUser;
import com.yunhefc.contracts.player.Position;
import com.yunhefc.contracts.security.AuthPrincipal;
import com.yunhefc.lineup.domain.FormationSlot;
import com.yunhefc.lineup.domain.FormationTemplate;
import com.yunhefc.lineup.dto.LineupDtos.FormationSlotView;
import com.yunhefc.lineup.dto.LineupDtos.FormationTemplateCreateRequest;
import com.yunhefc.lineup.dto.LineupDtos.FormationTemplateView;
import com.yunhefc.lineup.infrastructure.FormationSlotMapper;
import com.yunhefc.lineup.infrastructure.FormationTemplateMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FormationTemplateApplicationService {

    private static final int PRESET = 1;
    private static final int CUSTOM = 0;

    private final FormationTemplateMapper templateMapper;
    private final FormationSlotMapper slotMapper;

    @Transactional(readOnly = true)
    public List<FormationTemplateView> list(boolean includePreset) {
        Long teamId = CurrentUser.principal().teamId();
        LambdaQueryWrapper<FormationTemplate> query = new LambdaQueryWrapper<>();
        if (includePreset) {
            query.and(wrapper -> wrapper
                    .nested(preset -> preset.eq(FormationTemplate::getIsPreset, PRESET)
                            .isNull(FormationTemplate::getTeamId))
                    .or()
                    .nested(custom -> custom.eq(FormationTemplate::getTeamId, teamId)
                            .eq(FormationTemplate::getIsPreset, CUSTOM)));
        } else {
            query.eq(FormationTemplate::getTeamId, teamId)
                    .eq(FormationTemplate::getIsPreset, CUSTOM);
        }
        query.orderByDesc(FormationTemplate::getIsPreset)
                .orderByAsc(FormationTemplate::getId);
        return views(templateMapper.selectList(query));
    }

    /**
     * LIN-06：只允许复制预置阵型，槽位按原坐标复制后归属当前球队。
     */
    @Transactional
    public FormationTemplateView create(FormationTemplateCreateRequest request) {
        FormationTemplate base = templateMapper.selectById(request.baseTemplateId());
        if (base == null || !isPreset(base)) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "只能复制预置阵型");
        }
        String name = requireName(request.name());
        AuthPrincipal principal = CurrentUser.principal();

        FormationTemplate template = new FormationTemplate();
        template.setTeamId(principal.teamId());
        template.setName(name);
        template.setIsPreset(CUSTOM);
        template.setBaseTemplateId(base.getId());
        template.setCreatedBy(principal.userId());
        template.setIsDeleted(0);
        templateMapper.insert(template);

        List<FormationSlot> baseSlots = slots(base.getId());
        List<FormationSlot> copies = baseSlots.stream()
                .map(slot -> copy(slot, template.getId()))
                .toList();
        copies.forEach(slotMapper::insert);
        return view(template, copies);
    }

    @Transactional
    public FormationTemplateView rename(Long id, String name) {
        FormationTemplate template = requireCustom(id, "预置阵型不可重命名");
        template.setName(requireName(name));
        if (templateMapper.updateById(template) == 0) {
            // 读取后被并发软删除：UPDATE 受 is_deleted=0 约束命中 0 行。
            throw new BusinessException(ErrorCode.NOT_FOUND, "阵型模板不存在");
        }
        return view(template, slots(template.getId()));
    }

    /**
     * LIN-08：软删除模板本身，保留槽位与历史阵容引用。
     */
    @Transactional
    public void delete(Long id) {
        FormationTemplate template = requireCustom(id, "预置阵型不可删除");
        templateMapper.deleteById(template.getId());
    }

    private FormationTemplate requireCustom(Long id, String presetMessage) {
        Long teamId = CurrentUser.principal().teamId();
        FormationTemplate template = templateMapper.selectById(id);
        if (template == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "阵型模板不存在");
        }
        if (isPreset(template)) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, presetMessage);
        }
        if (!Objects.equals(template.getTeamId(), teamId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "阵型模板不存在");
        }
        return template;
    }

    private List<FormationTemplateView> views(List<FormationTemplate> templates) {
        if (templates.isEmpty()) {
            return List.of();
        }
        Map<Long, List<FormationSlot>> slotsByTemplate = slots(
                templates.stream().map(FormationTemplate::getId).toList()).stream()
                .collect(Collectors.groupingBy(FormationSlot::getTemplateId));
        return templates.stream()
                .map(template -> view(template,
                        slotsByTemplate.getOrDefault(template.getId(), List.of())))
                .toList();
    }

    private List<FormationSlot> slots(List<Long> templateIds) {
        return slotMapper.selectList(new LambdaQueryWrapper<FormationSlot>()
                .in(FormationSlot::getTemplateId, templateIds)
                .orderByAsc(FormationSlot::getSortOrder)
                .orderByAsc(FormationSlot::getId));
    }

    private List<FormationSlot> slots(Long templateId) {
        return slots(List.of(templateId));
    }

    private static FormationTemplateView view(FormationTemplate template,
                                              List<FormationSlot> slots) {
        return new FormationTemplateView(template.getId(), template.getName(),
                isPreset(template), template.getBaseTemplateId(), slots.stream()
                .sorted((left, right) -> Integer.compare(
                        sortOrder(left), sortOrder(right)))
                .map(slot -> new FormationSlotView(slot.getSlotKey(), slot.getRole(),
                        Position.of(slot.getRole()).label(), slot.getX(), slot.getY()))
                .toList());
    }

    private static FormationSlot copy(FormationSlot source, Long templateId) {
        FormationSlot slot = new FormationSlot();
        slot.setTemplateId(templateId);
        slot.setSlotKey(source.getSlotKey());
        slot.setRole(source.getRole());
        slot.setX(source.getX());
        slot.setY(source.getY());
        slot.setSortOrder(source.getSortOrder());
        slot.setIsDeleted(0);
        return slot;
    }

    private static int sortOrder(FormationSlot slot) {
        return slot.getSortOrder() == null ? 0 : slot.getSortOrder();
    }

    private static boolean isPreset(FormationTemplate template) {
        return template.getIsPreset() != null && template.getIsPreset() == PRESET;
    }

    private static String requireName(String name) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "模板名称不能为空");
        }
        return trimmed;
    }
}
