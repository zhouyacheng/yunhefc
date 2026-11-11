package com.yunhefc.registration.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yunhefc.common.error.BusinessException;
import com.yunhefc.common.error.ErrorCode;
import com.yunhefc.contracts.player.PlayerReadPort;
import com.yunhefc.contracts.player.PlayerSnapshot;
import com.yunhefc.contracts.registration.RegistrationQueueCounts;
import com.yunhefc.contracts.registration.RegistrationReadPort;
import com.yunhefc.contracts.registration.RegistrationRosterPage;
import com.yunhefc.contracts.registration.RegistrationSnapshot;
import com.yunhefc.registration.domain.Registration;
import com.yunhefc.registration.domain.RegistrationStatus;
import com.yunhefc.registration.domain.RegistrationType;
import com.yunhefc.registration.infrastructure.RegistrationMapper;
import com.yunhefc.registration.infrastructure.RegistrationQueueRow;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class RegistrationReadAdapter implements RegistrationReadPort {

    private final RegistrationMapper registrationMapper;
    private final PlayerReadPort playerReadPort;

    @Override
    public Map<Long, RegistrationQueueCounts> queueCounts(Collection<Long> matchIds) {
        if (matchIds == null || matchIds.isEmpty()) {
            return Map.of();
        }
        return registrationMapper.selectQueueCounts(matchIds).stream()
                .collect(Collectors.toMap(RegistrationQueueRow::getMatchId, RegistrationReadAdapter::toCounts));
    }

    @Override
    public Optional<RegistrationSnapshot> findActiveByUser(Long matchId, Long userId) {
        return playerReadPort.findByUserId(userId)
                .flatMap(player -> Optional.ofNullable(registrationMapper.selectOne(
                        new LambdaQueryWrapper<Registration>()
                                .eq(Registration::getMatchId, matchId)
                                .eq(Registration::getPlayerId, player.id())
                                .in(Registration::getStatus,
                                        RegistrationStatus.CONFIRMED, RegistrationStatus.WAITLISTED)
                                .last("LIMIT 1"))))
                .map(RegistrationReadAdapter::toSnapshot);
    }

    @Override
    public List<RegistrationSnapshot> findLatestByPlayer(Long playerId) {
        return registrationMapper.selectLatestByPlayer(playerId).stream()
                .map(RegistrationReadAdapter::toSnapshot)
                .toList();
    }

    @Override
    public RegistrationRosterPage findRoster(Long matchId, Integer type, Integer status,
                                             long page, long pageSize) {
        RegistrationType typeFilter = type == null ? null : parseType(type);
        RegistrationStatus statusFilter = status == null ? null : parseStatus(status);
        Page<Registration> result = registrationMapper.selectPage(new Page<>(page, pageSize),
                new LambdaQueryWrapper<Registration>()
                        .eq(Registration::getMatchId, matchId)
                        .eq(typeFilter != null, Registration::getType, typeFilter)
                        .eq(statusFilter != null, Registration::getStatus, statusFilter)
                        .orderByAsc(Registration::getRegisteredAt)
                        .orderByAsc(Registration::getId));
        return new RegistrationRosterPage(
                result.getRecords().stream().map(RegistrationReadAdapter::toSnapshot).toList(),
                result.getTotal());
    }

    @Override
    public List<Long> findConfirmedPlayerIds(Long matchId) {
        return registrationMapper.selectList(new LambdaQueryWrapper<Registration>()
                        .select(Registration::getPlayerId)
                        .eq(Registration::getMatchId, matchId)
                        .eq(Registration::getStatus, RegistrationStatus.CONFIRMED)
                        .orderByAsc(Registration::getRegisteredAt)
                        .orderByAsc(Registration::getId))
                .stream()
                .map(Registration::getPlayerId)
                .toList();
    }

    @Override
    public List<Long> findActiveUserIds(Long teamId, Long matchId) {
        List<Registration> registrations = registrationMapper.selectList(
                new LambdaQueryWrapper<Registration>()
                        .eq(Registration::getMatchId, matchId)
                        .in(Registration::getStatus,
                                RegistrationStatus.CONFIRMED, RegistrationStatus.WAITLISTED)
                        .orderByAsc(Registration::getRegisteredAt)
                        .orderByAsc(Registration::getId));
        if (registrations.isEmpty()) {
            return List.of();
        }
        Map<Long, PlayerSnapshot> players = playerReadPort.findByIds(teamId,
                        registrations.stream().map(Registration::getPlayerId).toList())
                .stream()
                .collect(Collectors.toMap(PlayerSnapshot::id, Function.identity()));
        return registrations.stream()
                .map(registration -> players.get(registration.getPlayerId()))
                .filter(player -> player != null)
                .map(PlayerSnapshot::userId)
                .distinct()
                .toList();
    }

    private static RegistrationType parseType(Integer type) {
        try {
            return RegistrationType.of(type);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "type 必须为 0 或 1");
        }
    }

    private static RegistrationStatus parseStatus(Integer status) {
        try {
            return RegistrationStatus.of(status);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "status 必须为 0–3");
        }
    }

    private static RegistrationQueueCounts toCounts(RegistrationQueueRow row) {
        return new RegistrationQueueCounts(
                row.getConfirmedCount(), row.getGuestCount(), row.getWaitlistCount());
    }

    private static RegistrationSnapshot toSnapshot(Registration registration) {
        return new RegistrationSnapshot(
                registration.getId(),
                registration.getMatchId(),
                registration.getPlayerId(),
                registration.getType().code(),
                registration.getType().label(),
                registration.getStatus().code(),
                registration.getStatus().label(),
                registration.getRegisteredAt(),
                registration.getCancelledAt(),
                registration.getStatus() == RegistrationStatus.CONFIRMED
                        || registration.getStatus() == RegistrationStatus.WAITLISTED);
    }
}
