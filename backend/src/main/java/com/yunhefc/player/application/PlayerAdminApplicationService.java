package com.yunhefc.player.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yunhefc.audit.application.AuditRecorder;
import com.yunhefc.common.api.PageQuery;
import com.yunhefc.common.api.PageResponse;
import com.yunhefc.common.error.BusinessException;
import com.yunhefc.common.error.ErrorCode;
import com.yunhefc.common.security.CurrentUser;
import com.yunhefc.contracts.player.PlayerType;
import com.yunhefc.contracts.player.Position;
import com.yunhefc.contracts.security.UserAccountReadPort;
import com.yunhefc.contracts.security.UserAccountSnapshot;
import com.yunhefc.player.domain.Player;
import com.yunhefc.player.domain.SecondaryPositions;
import com.yunhefc.player.dto.PlayerDtos.PlayerAdminQuery;
import com.yunhefc.player.dto.PlayerDtos.PlayerAdminView;
import com.yunhefc.player.dto.PlayerDtos.PlayerUpdateRequest;
import com.yunhefc.player.infrastructure.PlayerMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PlayerAdminApplicationService {

    private final PlayerMapper playerMapper;
    private final UserAccountReadPort userAccountReadPort;
    private final AuditRecorder auditRecorder;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public PageResponse<PlayerAdminView> list(PlayerAdminQuery query) {
        Long teamId = CurrentUser.principal().teamId();
        PageQuery page = PageQuery.of(query.page(), query.pageSize());
        String name = normalize(query.name());
        String region = normalize(query.region());
        validateLength(name, 32, "姓名");
        validateLength(region, 64, "地区");
        PlayerType type = playerType(query.type());
        Position position = position(query.position());
        validateStatus(query.status());
        validateSkill(query.skill());

        Set<Long> userIds = null;
        if (query.status() != null || query.admin() != null) {
            userIds = userAccountReadPort.findUserIds(query.status(), query.admin());
        }

        LambdaQueryWrapper<Player> wrapper = new LambdaQueryWrapper<Player>()
                .eq(Player::getTeamId, teamId)
                .like(name != null, Player::getName, name)
                .like(region != null, Player::getRegion, region)
                .eq(type != null, Player::getType, type)
                .eq(position != null, Player::getPosition, position)
                .eq(query.skill() != null, Player::getSkill, query.skill())
                .in(userIds != null, Player::getUserId, userIds);
        applySort(wrapper, query.sort());
        if (userIds != null && userIds.isEmpty()) {
            return PageResponse.of(List.of(), 0, page.page(), page.pageSize());
        }

        Page<Player> result = playerMapper.selectPage(new Page<>(page.page(), page.pageSize()), wrapper);
        Map<Long, UserAccountSnapshot> accounts = userAccountReadPort.findByIds(
                result.getRecords().stream().map(Player::getUserId).toList());
        List<PlayerAdminView> items = result.getRecords().stream()
                .map(player -> toView(player, accounts.get(player.getUserId())))
                .toList();
        return PageResponse.of(items, result.getTotal(), page.page(), page.pageSize());
    }

    /**
     * 账号生命周期编排在审核事务内组装响应；teamId 由调用方传入，避免依赖调用方安全上下文。
     */
    @Transactional(readOnly = true)
    public PlayerAdminView adminView(Long teamId, Long playerId) {
        Player player = requirePlayer(teamId, playerId);
        return toView(player, userAccountReadPort.find(player.getUserId()).orElse(null));
    }

    @Transactional
    public PlayerAdminView update(Long playerId, PlayerUpdateRequest request) {
        Long teamId = CurrentUser.principal().teamId();
        Player player = requirePlayer(teamId, playerId);

        String name = normalizeName(request.name());
        boolean regionProvided = request.region() != null;
        String region = regionProvided ? normalize(request.region()) : null;
        boolean avatarProvided = request.avatarUrl() != null;
        String avatarUrl = avatarProvided ? normalize(request.avatarUrl()) : null;
        Position newPosition = position(request.position());
        int effectivePosition = newPosition == null ? player.getPosition().code() : newPosition.code();
        validateJersey(request.jersey());
        validateSkill(request.skill());

        boolean secondaryProvided = request.secondaryPositions() != null;
        List<Integer> secondary = null;
        if (secondaryProvided) {
            secondary = validateSecondary(request.secondaryPositions(), effectivePosition);
        } else if (newPosition != null) {
            List<Integer> existing = SecondaryPositions.parse(objectMapper, player.getSecondaryPositions());
            if (existing.contains(effectivePosition)) {
                secondary = existing.stream().filter(code -> code != effectivePosition).toList();
            }
        }

        if (request.jersey() != null && player.getType() == PlayerType.PLAYER) {
            requireJerseyAvailable(teamId, playerId, request.jersey());
        }

        boolean anyChange = name != null || request.jersey() != null || regionProvided || newPosition != null
                || secondaryProvided || request.skill() != null || avatarProvided;
        if (!anyChange) {
            return toView(player, userAccountReadPort.find(player.getUserId()).orElse(null));
        }

        LambdaUpdateWrapper<Player> update = new LambdaUpdateWrapper<Player>()
                .eq(Player::getId, playerId)
                .eq(Player::getTeamId, teamId)
                .set(name != null, Player::getName, name)
                .set(request.jersey() != null, Player::getJersey, request.jersey())
                .set(newPosition != null, Player::getPosition, newPosition)
                .set(secondaryProvided || secondary != null, Player::getSecondaryPositions,
                        secondary == null ? null : toJson(secondary))
                .set(regionProvided, Player::getRegion, region)
                .set(avatarProvided, Player::getAvatarUrl, avatarUrl)
                .set(request.skill() != null, Player::getSkill, request.skill());
        try {
            playerMapper.update(null, update);
        } catch (DuplicateKeyException ex) {
            throw new BusinessException(ErrorCode.DUPLICATE_JERSEY);
        }

        Player updated = playerMapper.selectById(playerId);
        if (updated == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "球员档案不存在");
        }
        auditRecorder.recordCurrent("PLAYER_UPDATE", "PLAYER", playerId, snapshot(player), snapshot(updated));
        return toView(updated, userAccountReadPort.find(updated.getUserId()).orElse(null));
    }

    private Player requirePlayer(Long teamId, Long playerId) {
        Player player = playerMapper.selectOne(new LambdaQueryWrapper<Player>()
                .eq(Player::getId, playerId)
                .eq(Player::getTeamId, teamId));
        if (player == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "球员档案不存在");
        }
        return player;
    }

    private void requireJerseyAvailable(Long teamId, Long playerId, Integer jersey) {
        Long count = playerMapper.selectCount(new LambdaQueryWrapper<Player>()
                .eq(Player::getTeamId, teamId)
                .eq(Player::getType, PlayerType.PLAYER)
                .eq(Player::getJersey, jersey)
                .ne(Player::getId, playerId));
        if (count > 0) {
            throw new BusinessException(ErrorCode.DUPLICATE_JERSEY);
        }
    }

    private PlayerAdminView toView(Player player, UserAccountSnapshot account) {
        List<Integer> secondary = SecondaryPositions.parse(objectMapper, player.getSecondaryPositions());
        return new PlayerAdminView(
                player.getId(),
                player.getName(),
                player.getJersey(),
                player.getPosition().code(),
                player.getPosition().label(),
                secondary,
                secondary.stream().map(code -> Position.of(code).label()).toList(),
                player.getRegion(),
                player.getAvatarUrl(),
                player.getSkill(),
                player.getType().code(),
                player.getType().label(),
                account == null ? null : account.userId(),
                account == null ? null : account.phoneMasked(),
                account == null ? null : account.status(),
                account == null ? null : account.statusLabel(),
                account != null && account.admin(),
                account == null ? Set.of() : account.roles(),
                account == null ? null : account.createdAt());
    }

    private Map<String, Object> snapshot(Player player) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("name", player.getName());
        values.put("jersey", player.getJersey());
        values.put("type", player.getType().code());
        values.put("position", player.getPosition().code());
        values.put("secondaryPositions", SecondaryPositions.parse(objectMapper, player.getSecondaryPositions()));
        values.put("region", player.getRegion());
        values.put("avatarUrl", player.getAvatarUrl());
        values.put("skill", player.getSkill());
        return values;
    }

    private List<Integer> validateSecondary(List<Integer> positions, int primary) {
        for (Integer code : positions) {
            if (code == null || code < 0 || code > 3) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, "副位置必须为 0–3");
            }
        }
        Set<Integer> unique = new LinkedHashSet<>(positions);
        if (unique.size() != positions.size()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "副位置不能重复");
        }
        if (unique.contains(primary)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "副位置不能包含主位置");
        }
        return List.copyOf(unique);
    }

    private String toJson(List<Integer> positions) {
        try {
            return objectMapper.writeValueAsString(positions);
        } catch (JsonProcessingException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "副位置数据格式错误");
        }
    }

    private void applySort(LambdaQueryWrapper<Player> wrapper, String sort) {
        String value = sort == null || sort.isBlank() ? "jersey:asc" : sort.trim();
        switch (value) {
            case "default", "jersey:asc" -> wrapper.orderByAsc(Player::getJersey);
            case "jersey:desc" -> wrapper.orderByDesc(Player::getJersey);
            case "skill:asc" -> wrapper.orderByAsc(Player::getSkill);
            case "skill:desc" -> wrapper.orderByDesc(Player::getSkill);
            case "position:asc" -> wrapper.orderByAsc(Player::getPosition);
            case "position:desc" -> wrapper.orderByDesc(Player::getPosition);
            case "region:asc" -> wrapper.orderByAsc(Player::getRegion);
            case "region:desc" -> wrapper.orderByDesc(Player::getRegion);
            default -> throw new BusinessException(ErrorCode.VALIDATION_FAILED, "排序参数不合法");
        }
        wrapper.orderByAsc(Player::getId);
    }

    private PlayerType playerType(Integer code) {
        if (code == null) {
            return null;
        }
        try {
            return PlayerType.of(code);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "球员类型不合法");
        }
    }

    private Position position(Integer code) {
        if (code == null) {
            return null;
        }
        try {
            return Position.of(code);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "位置不合法");
        }
    }

    private void validateStatus(Integer status) {
        if (status != null && (status < 0 || status > 2)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "账号状态不合法");
        }
    }

    private void validateSkill(Integer skill) {
        if (skill != null && (skill < 1 || skill > 5)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "星级必须为 1–5");
        }
    }

    private void validateJersey(Integer jersey) {
        if (jersey != null && (jersey < 0 || jersey > 65535)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "号码必须为 0–65535");
        }
    }

    private void validateLength(String value, int max, String field) {
        if (value != null && value.length() > max) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, field + "长度不能超过 " + max);
        }
    }

    private String normalizeName(String name) {
        if (name == null) {
            return null;
        }
        String stripped = name.strip();
        if (stripped.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "姓名不能为空");
        }
        return stripped;
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String stripped = value.strip();
        return stripped.isBlank() ? null : stripped;
    }
}
