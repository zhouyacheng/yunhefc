package com.yunhefc.event.application;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yunhefc.audit.application.AuditRecorder;
import com.yunhefc.common.api.RequestContext;
import com.yunhefc.common.error.BusinessException;
import com.yunhefc.common.error.ErrorCode;
import com.yunhefc.common.security.CurrentUser;
import com.yunhefc.contracts.match.MatchReadPort;
import com.yunhefc.contracts.match.MatchSnapshot;
import com.yunhefc.contracts.player.PlayerReadPort;
import com.yunhefc.contracts.player.PlayerSnapshot;
import com.yunhefc.contracts.player.Position;
import com.yunhefc.contracts.registration.RegistrationReadPort;
import com.yunhefc.contracts.security.AuthPrincipal;
import com.yunhefc.contracts.security.UserAccountReadPort;
import com.yunhefc.contracts.security.UserAccountSnapshot;
import com.yunhefc.contracts.team.TeamReadPort;
import com.yunhefc.event.domain.EventHalf;
import com.yunhefc.event.domain.EventHistory;
import com.yunhefc.event.domain.EventRules;
import com.yunhefc.event.domain.EventScore;
import com.yunhefc.event.domain.EventTeam;
import com.yunhefc.event.domain.EventType;
import com.yunhefc.event.domain.MatchEvent;
import com.yunhefc.event.dto.EventDtos.DerivedScoreView;
import com.yunhefc.event.dto.EventDtos.EventCreateRequest;
import com.yunhefc.event.dto.EventDtos.EventPlayer;
import com.yunhefc.event.dto.EventDtos.EventTimeline;
import com.yunhefc.event.dto.EventDtos.EventUpdateRequest;
import com.yunhefc.event.dto.EventDtos.EventView;
import com.yunhefc.event.infrastructure.EventHistoryMapper;
import com.yunhefc.event.infrastructure.MatchEventMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class EventApplicationService {

    private static final Logger log = LoggerFactory.getLogger(EventApplicationService.class);
    private static final String EVENT_EDIT_ANY = "event:edit:any";
    private static final int ACTIVE_ACCOUNT = 1;
    private static final Duration PLAYER_EDIT_WINDOW = Duration.ofHours(24);

    private final MatchEventMapper matchEventMapper;
    private final EventHistoryMapper eventHistoryMapper;
    private final MatchReadPort matchReadPort;
    private final PlayerReadPort playerReadPort;
    private final RegistrationReadPort registrationReadPort;
    private final UserAccountReadPort userAccountReadPort;
    private final TeamReadPort teamReadPort;
    private final AuditRecorder auditRecorder;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    /**
     * EVT-01：公开时间线；软删除需同时排除 is_deleted 与 deleted_at。
     */
    @Transactional(readOnly = true)
    public EventTimeline timeline(Long matchId) {
        MatchSnapshot match = matchReadPort.findById(matchId)
                .filter(item -> item.teamId().equals(teamReadPort.currentTeamId()))
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "比赛不存在"));
        List<MatchEvent> events = matchEventMapper.selectTimeline(matchId);
        Map<Long, PlayerSnapshot> players = loadPlayers(match.teamId(), events);
        List<EventView> items = events.stream()
                .map(event -> toView(event, players))
                .toList();
        DerivedScoreView derived = events.isEmpty() ? null : toDerived(EventScore.from(events));
        return new EventTimeline(items, derived);
    }

    /**
     * EVT-02：仅进行中/已结束；球员创建限进行中或赛后 24h，换人仅管理员；
     * 对外比赛锁定本人所属队与本人事件，队内/训练赛可为本场任意球员创建。
     */
    @Transactional
    public EventView create(Long matchId, EventCreateRequest request) {
        AuthPrincipal principal = CurrentUser.principal();
        MatchSnapshot match = requireMatch(matchId, principal.teamId());
        requireEventStatus(match);
        EventRules.EventFields fields = toFields(request);
        boolean admin = isAdmin(principal);

        if (!admin) {
            requirePlayerCreateWindow(match);
            if (fields.type() == EventType.SUBSTITUTION) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "换人事件仅管理员可录入");
            }
            if (!match.internalOrTraining()) {
                requireOwnTeamAndPlayer(match, principal, fields);
            }
        }
        requireEligiblePlayers(match, selectedPlayerIds(fields));

        Instant now = Instant.now(clock);
        MatchEvent event = new MatchEvent();
        event.setMatchId(matchId);
        apply(event, fields);
        event.setCreatedBy(principal.userId());
        event.setCreatedAt(now);
        event.setUpdatedAt(now);
        event.setVersion(0);
        matchEventMapper.insert(event);

        recordHistory(event.getId(), EventHistory.CREATE, null, auditSnapshot(event), principal);
        auditRecorder.recordCurrent("EVENT_CREATE", "EVENT", event.getId(), null, auditSnapshot(event));
        log.info("event created, eventId={}, matchId={}, type={}, creator={}",
                event.getId(), matchId, event.getType(), principal.userId());
        return toView(event, loadPlayers(match.teamId(), List.of(event)));
    }

    /**
     * EVT-03：合并后的有效事件参与字段矩阵与选人校验；球员仅改自己的事件且赛后 24h，
     * 管理员不限；换人仅管理员；已取消/已延期 40905；版本冲突 40901。
     */
    @Transactional
    public EventView update(Long eventId, EventUpdateRequest request) {
        AuthPrincipal principal = CurrentUser.principal();
        MatchEvent event = Optional.ofNullable(matchEventMapper.selectById(eventId))
                .filter(item -> item.getDeletedAt() == null)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "事件不存在"));
        MatchSnapshot match = requireMatch(event.getMatchId(), principal.teamId());
        requireMatchActive(match);

        if (!request.getVersion().equals(event.getVersion())) {
            throw new BusinessException(ErrorCode.VERSION_CONFLICT);
        }
        EventRules.EventFields merged = merge(event, request);
        boolean admin = isAdmin(principal);
        if (!admin) {
            requireOwnEvent(match, principal, event, merged);
        }
        requireEligiblePlayers(match, selectedPlayerIds(merged));

        Map<String, Object> before = auditSnapshot(event);
        Instant now = Instant.now(clock);
        apply(event, merged);
        int updated = matchEventMapper.update(null, new LambdaUpdateWrapper<MatchEvent>()
                .eq(MatchEvent::getId, eventId)
                .eq(MatchEvent::getVersion, request.getVersion())
                .isNull(MatchEvent::getDeletedAt)
                .set(MatchEvent::getType, merged.type())
                .set(MatchEvent::getTeam, merged.team())
                .set(MatchEvent::getPlayerId, merged.playerId())
                .set(MatchEvent::getAssistPlayerId, merged.assistPlayerId())
                .set(MatchEvent::getInPlayerId, merged.inPlayerId())
                .set(MatchEvent::getOutPlayerId, merged.outPlayerId())
                .set(MatchEvent::getMinute, merged.minute())
                .set(MatchEvent::getHalf, merged.half())
                .set(MatchEvent::getPenalty, merged.penalty())
                .set(MatchEvent::getUpdatedBy, principal.userId())
                .set(MatchEvent::getUpdatedAt, now)
                .set(MatchEvent::getVersion, request.getVersion() + 1));
        if (updated == 0) {
            MatchEvent current = matchEventMapper.selectById(eventId);
            if (current == null || current.getDeletedAt() != null) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "事件不存在");
            }
            throw new BusinessException(ErrorCode.VERSION_CONFLICT);
        }
        event.setVersion(request.getVersion() + 1);
        event.setUpdatedAt(now);
        event.setUpdatedBy(principal.userId());

        recordHistory(eventId, EventHistory.UPDATE, before, auditSnapshot(event), principal);
        auditRecorder.recordCurrent("EVENT_UPDATE", "EVENT", eventId, before, auditSnapshot(event));
        log.info("event updated, eventId={}, matchId={}, version={}, editor={}",
                eventId, event.getMatchId(), event.getVersion(), principal.userId());
        return toView(event, loadPlayers(match.teamId(), List.of(event)));
    }

    private void requireOwnTeamAndPlayer(MatchSnapshot match, AuthPrincipal principal,
                                         EventRules.EventFields fields) {
        if (!Objects.equals(fields.team().code(), match.ourEventTeam())) {
            throw violation("对外比赛只能录入我方球队的事件");
        }
        PlayerSnapshot own = requireOwnPlayer(principal, ErrorCode.BUSINESS_RULE_VIOLATION,
                "无球员档案，不能录入事件");
        if (!own.id().equals(fields.playerId())) {
            throw violation("只能录入自己的事件");
        }
    }

    private void requireOwnEvent(MatchSnapshot match, AuthPrincipal principal, MatchEvent event,
                                 EventRules.EventFields merged) {
        if (event.getType() == EventType.SUBSTITUTION || merged.type() == EventType.SUBSTITUTION) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "换人事件仅管理员可修改");
        }
        PlayerSnapshot own = requireOwnPlayer(principal, ErrorCode.FORBIDDEN,
                "只能修改自己的事件");
        if (event.getPlayerId() == null || !event.getPlayerId().equals(own.id())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只能修改自己的事件");
        }
        requirePlayerWindow(match, "已超过赛后 24 小时可修改窗口");
        if (!own.id().equals(merged.playerId())) {
            throw violation("只能修改自己的事件");
        }
        if (!match.internalOrTraining()
                && !Objects.equals(merged.team().code(), match.ourEventTeam())) {
            throw violation("对外比赛只能修改我方球队的事件");
        }
    }

    private void requirePlayerCreateWindow(MatchSnapshot match) {
        requirePlayerWindow(match, "已超过赛后 24 小时可录入窗口");
    }

    private void requirePlayerWindow(MatchSnapshot match, String message) {
        if (match.finished() && Instant.now(clock).isAfter(editWindowEnd(match))) {
            throw violation(message);
        }
    }

    private PlayerSnapshot requireOwnPlayer(AuthPrincipal principal, ErrorCode errorCode,
                                            String message) {
        return playerReadPort.findByUserId(principal.userId())
                .orElseThrow(() -> new BusinessException(errorCode, message));
    }

    private void requireEligiblePlayers(MatchSnapshot match, Set<Long> playerIds) {
        if (playerIds.isEmpty()) {
            return;
        }
        Set<Long> confirmed = Set.copyOf(registrationReadPort.findConfirmedPlayerIds(match.id()));
        if (!confirmed.containsAll(playerIds)) {
            throw violation("球员须为本场已确认报名的球员");
        }
        List<PlayerSnapshot> players = playerReadPort.findByIds(match.teamId(), playerIds);
        if (players.size() != playerIds.size()) {
            throw violation("球员不存在或不属于本队");
        }
        Map<Long, UserAccountSnapshot> accounts = userAccountReadPort.findByIds(
                players.stream().map(PlayerSnapshot::userId).toList());
        boolean active = players.stream()
                .map(player -> accounts.get(player.userId()))
                .allMatch(account -> account != null && account.status() != null
                        && account.status() == ACTIVE_ACCOUNT);
        if (!active) {
            throw violation("球员账号不可用");
        }
    }

    private void requireEventStatus(MatchSnapshot match) {
        if (match.cancelledOrPostponed()) {
            throw new BusinessException(ErrorCode.MATCH_STATE_CONFLICT, "比赛已取消或已延期，不能录入事件");
        }
        if (!match.inProgressOrFinished()) {
            throw new BusinessException(ErrorCode.MATCH_STATE_CONFLICT, "仅进行中或已结束的比赛可录入事件");
        }
    }

    private void requireMatchActive(MatchSnapshot match) {
        if (match.cancelledOrPostponed()) {
            throw new BusinessException(ErrorCode.MATCH_STATE_CONFLICT, "比赛已取消或已延期，不能修改事件");
        }
    }

    private Instant editWindowEnd(MatchSnapshot match) {
        return match.endAt().plus(PLAYER_EDIT_WINDOW);
    }

    private MatchSnapshot requireMatch(Long matchId, Long teamId) {
        return matchReadPort.findById(matchId)
                .filter(match -> match.teamId().equals(teamId))
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "比赛不存在"));
    }

    private EventRules.EventFields toFields(EventCreateRequest request) {
        EventRules.EventFields fields = new EventRules.EventFields(
                parseType(request.type()),
                parseTeam(request.team()),
                request.playerId(),
                request.assistPlayerId(),
                request.inPlayerId(),
                request.outPlayerId(),
                request.minute(),
                parseHalf(request.half()),
                Boolean.TRUE.equals(request.penalty()));
        EventRules.validate(fields);
        return fields;
    }

    private EventRules.EventFields merge(MatchEvent event, EventUpdateRequest request) {
        EventType type = request.getType() == null ? event.getType() : parseType(request.getType());
        EventTeam team = request.getTeam() == null ? event.getTeam() : parseTeam(request.getTeam());
        EventHalf half = request.getHalf() == null ? event.getHalf() : parseHalf(request.getHalf());
        Integer minute = request.getMinute() == null ? event.getMinute() : request.getMinute();
        Long playerId = request.has("playerId") ? request.getPlayerId() : event.getPlayerId();
        Long assistPlayerId = request.has("assistPlayerId")
                ? request.getAssistPlayerId() : event.getAssistPlayerId();
        Long inPlayerId = request.has("inPlayerId") ? request.getInPlayerId() : event.getInPlayerId();
        Long outPlayerId = request.has("outPlayerId")
                ? request.getOutPlayerId() : event.getOutPlayerId();
        boolean penalty = request.has("penalty")
                ? Boolean.TRUE.equals(request.getPenalty())
                : Boolean.TRUE.equals(event.getPenalty());
        EventRules.EventFields fields = new EventRules.EventFields(
                type, team, playerId, assistPlayerId, inPlayerId, outPlayerId,
                minute, half, penalty);
        EventRules.validate(fields);
        return fields;
    }

    private void apply(MatchEvent event, EventRules.EventFields fields) {
        event.setType(fields.type());
        event.setTeam(fields.team());
        event.setPlayerId(fields.playerId());
        event.setAssistPlayerId(fields.assistPlayerId());
        event.setInPlayerId(fields.inPlayerId());
        event.setOutPlayerId(fields.outPlayerId());
        event.setMinute(fields.minute());
        event.setHalf(fields.half());
        event.setPenalty(fields.penalty());
    }

    private Set<Long> selectedPlayerIds(EventRules.EventFields fields) {
        return Stream.of(fields.playerId(), fields.assistPlayerId(),
                        fields.inPlayerId(), fields.outPlayerId())
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    private Map<Long, PlayerSnapshot> loadPlayers(Long teamId, Collection<MatchEvent> events) {
        Set<Long> ids = events.stream()
                .flatMap(event -> Stream.of(event.getPlayerId(), event.getAssistPlayerId(),
                        event.getInPlayerId(), event.getOutPlayerId()))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        return playerReadPort.findByIds(teamId, ids).stream()
                .collect(Collectors.toMap(PlayerSnapshot::id, Function.identity()));
    }

    private void recordHistory(Long eventId, int action, Object before, Object after,
                               AuthPrincipal principal) {
        EventHistory history = new EventHistory();
        history.setEventId(eventId);
        history.setAction(action);
        history.setBeforeJson(json(before));
        history.setAfterJson(json(after));
        history.setActorUserId(principal.userId());
        history.setRequestId(RequestContext.requestId());
        history.setCreatedAt(Instant.now(clock));
        eventHistoryMapper.insert(history);
    }

    private String json(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("无法序列化事件快照", ex);
        }
    }

    private static Map<String, Object> auditSnapshot(MatchEvent event) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("matchId", event.getMatchId());
        values.put("team", event.getTeam().code());
        values.put("type", event.getType().code());
        values.put("playerId", event.getPlayerId());
        values.put("assistPlayerId", event.getAssistPlayerId());
        values.put("inPlayerId", event.getInPlayerId());
        values.put("outPlayerId", event.getOutPlayerId());
        values.put("minute", event.getMinute());
        values.put("half", event.getHalf().code());
        values.put("penalty", event.getPenalty());
        values.put("version", event.getVersion());
        return values;
    }

    private static EventView toView(MatchEvent event, Map<Long, PlayerSnapshot> players) {
        return new EventView(
                event.getId(),
                event.getType().code(),
                event.getType().label(),
                event.getTeam().code(),
                event.getTeam().label(),
                player(players, event.getPlayerId()),
                player(players, event.getAssistPlayerId()),
                player(players, event.getInPlayerId()),
                player(players, event.getOutPlayerId()),
                event.getMinute(),
                event.getHalf().code(),
                event.getHalf().label(),
                Boolean.TRUE.equals(event.getPenalty()),
                event.getCreatedBy(),
                event.getUpdatedAt(),
                event.getVersion());
    }

    private static EventPlayer player(Map<Long, PlayerSnapshot> players, Long playerId) {
        if (playerId == null) {
            return null;
        }
        PlayerSnapshot snapshot = players.get(playerId);
        if (snapshot == null) {
            return null;
        }
        Position position = Position.of(snapshot.position());
        return new EventPlayer(snapshot.id(), snapshot.name(), snapshot.jersey(),
                position.code(), position.label());
    }

    private static DerivedScoreView toDerived(EventScore score) {
        return new DerivedScoreView(score.scoreHome(), score.scoreAway());
    }

    private static boolean isAdmin(AuthPrincipal principal) {
        return principal.permissions().contains(EVENT_EDIT_ANY);
    }

    private static EventType parseType(Integer type) {
        try {
            return EventType.of(type);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "type 必须为 0–4");
        }
    }

    private static EventTeam parseTeam(Integer team) {
        try {
            return EventTeam.of(team);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "team 必须为 0 或 1");
        }
    }

    private static EventHalf parseHalf(Integer half) {
        try {
            return EventHalf.of(half);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "half 必须为 0 或 1");
        }
    }

    private static BusinessException violation(String message) {
        return new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, message);
    }
}
