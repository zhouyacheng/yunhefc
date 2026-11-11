package com.yunhefc.lineup.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.yunhefc.audit.application.AuditRecorder;
import com.yunhefc.common.error.BusinessException;
import com.yunhefc.common.error.ErrorCode;
import com.yunhefc.common.security.CurrentUser;
import com.yunhefc.contracts.grouping.GroupingReadPort;
import com.yunhefc.contracts.grouping.SavedGroupingSnapshot;
import com.yunhefc.contracts.match.MatchReadPort;
import com.yunhefc.contracts.match.MatchSnapshot;
import com.yunhefc.contracts.player.PlayerReadPort;
import com.yunhefc.contracts.player.PlayerSnapshot;
import com.yunhefc.contracts.player.Position;
import com.yunhefc.contracts.registration.ConfirmedRegistration;
import com.yunhefc.contracts.registration.RegistrationReadPort;
import com.yunhefc.contracts.security.AuthPrincipal;
import com.yunhefc.contracts.security.UserAccountReadPort;
import com.yunhefc.contracts.security.UserAccountSnapshot;
import com.yunhefc.lineup.domain.FormationSlot;
import com.yunhefc.lineup.domain.FormationTemplate;
import com.yunhefc.lineup.domain.Lineup;
import com.yunhefc.lineup.domain.LineupRules;
import com.yunhefc.lineup.domain.LineupSlot;
import com.yunhefc.lineup.dto.LineupDtos.FormationSlotView;
import com.yunhefc.lineup.dto.LineupDtos.FormationTemplateView;
import com.yunhefc.lineup.dto.LineupDtos.LineupAdminView;
import com.yunhefc.lineup.dto.LineupDtos.LineupPlayerView;
import com.yunhefc.lineup.dto.LineupDtos.LineupSaveRequest;
import com.yunhefc.lineup.dto.LineupDtos.LineupSaveResponse;
import com.yunhefc.lineup.dto.LineupDtos.LineupStarterView;
import com.yunhefc.lineup.dto.LineupDtos.LineupView;
import com.yunhefc.lineup.infrastructure.FormationSlotMapper;
import com.yunhefc.lineup.infrastructure.FormationTemplateMapper;
import com.yunhefc.lineup.infrastructure.LineupMapper;
import com.yunhefc.lineup.infrastructure.LineupSlotMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LineupApplicationService {

    private static final Logger log = LoggerFactory.getLogger(LineupApplicationService.class);
    private static final int PRESET = 1;
    private static final int CUSTOM = 0;
    private static final int ACTIVE_ACCOUNT = 1;

    private final LineupMapper lineupMapper;
    private final LineupSlotMapper lineupSlotMapper;
    private final FormationTemplateMapper templateMapper;
    private final FormationSlotMapper formationSlotMapper;
    private final MatchReadPort matchReadPort;
    private final RegistrationReadPort registrationReadPort;
    private final PlayerReadPort playerReadPort;
    private final UserAccountReadPort userAccountReadPort;
    private final GroupingReadPort groupingReadPort;
    private final AuditRecorder auditRecorder;
    private final Clock clock;

    @Transactional(readOnly = true)
    public LineupView viewForPlayer(Long matchId, Integer side) {
        AuthPrincipal principal = CurrentUser.principal();
        int resolvedSide = LineupRules.requireSide(side);
        MatchSnapshot match = requireMatch(matchId, principal.teamId());
        State state = loadState(match, resolvedSide);
        boolean opponent = LineupRules.opponentSide(match, resolvedSide);
        return new LineupView(resolvedSide, formationView(state),
                starterViews(state, opponent, match.teamId()),
                benchViews(state, opponent, match, resolvedSide));
    }

    @Transactional(readOnly = true)
    public LineupAdminView adminView(Long matchId, Integer side) {
        AuthPrincipal principal = CurrentUser.principal();
        int resolvedSide = LineupRules.requireSide(side);
        MatchSnapshot match = requireMatch(matchId, principal.teamId());
        State state = loadState(match, resolvedSide);
        boolean opponent = LineupRules.opponentSide(match, resolvedSide);
        Lineup lineup = state.lineup();
        return new LineupAdminView(resolvedSide, formationView(state),
                starterViews(state, opponent, match.teamId()),
                benchViews(state, opponent, match, resolvedSide),
                lineup == null ? 0 : lineup.getVersion(),
                lineup == null ? null : lineup.getUpdatedAt(),
                lineup == null ? null : lineup.getUpdatedBy());
    }

    /**
     * LIN-03：我方首发必须排满所选阵型的全部槽位；有已保存分组时按 HOME=A、AWAY=B 限制球员。
     * 版本冲突返回 40901 与当前版本；终态/延期返回 40905。
     */
    @Transactional
    public LineupSaveResponse save(Long matchId, Integer side, LineupSaveRequest request) {
        AuthPrincipal principal = CurrentUser.principal();
        int resolvedSide = LineupRules.requireSide(side);
        MatchSnapshot match = matchReadPort.findByIdForUpdate(matchId, principal.teamId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "比赛不存在"));
        if (!LineupRules.editable(match)) {
            throw new BusinessException(ErrorCode.MATCH_STATE_CONFLICT,
                    "比赛已结束、已取消或已延期，不能编辑阵容");
        }
        boolean opponent = LineupRules.opponentSide(match, resolvedSide);

        FormationTemplate template = requireSelectableTemplate(
                request.formationTemplateId(), principal.teamId());
        List<FormationSlot> formationSlots = formationSlots(template.getId());
        List<String> slotKeys = formationSlots.stream().map(FormationSlot::getSlotKey).toList();
        List<LineupRules.Starter> starters = request.starters().stream()
                .map(slot -> new LineupRules.Starter(slot.slotKey(), slot.playerId()))
                .toList();
        LineupRules.validateSelection(slotKeys, starters, request.bench(), opponent);
        if (!opponent) {
            requireEligible(match, resolvedSide, starters, request.bench());
        }

        Lineup existing = lineupMapper.selectByMatchAndSide(matchId, resolvedSide);
        List<LineupSlot> beforeSlots = existing == null
                ? List.of() : lineupSlotMapper.selectByLineupId(existing.getId());
        int currentVersion = existing == null ? 0 : existing.getVersion();
        if (request.version() != currentVersion) {
            throw versionConflict(currentVersion);
        }

        Instant now = Instant.now(clock);
        Long lineupId;
        int newVersion = currentVersion + 1;
        if (existing == null) {
            Lineup lineup = new Lineup();
            lineup.setMatchId(matchId);
            lineup.setSide(resolvedSide);
            lineup.setFormationTemplateId(template.getId());
            lineup.setVersion(newVersion);
            lineup.setUpdatedBy(principal.userId());
            lineup.setIsDeleted(0);
            lineupMapper.insert(lineup);
            lineupId = lineup.getId();
        } else {
            lineupId = existing.getId();
            int updated = lineupMapper.update(null, new LambdaUpdateWrapper<Lineup>()
                    .eq(Lineup::getId, existing.getId())
                    .eq(Lineup::getVersion, currentVersion)
                    .eq(Lineup::getIsDeleted, 0)
                    .set(Lineup::getFormationTemplateId, template.getId())
                    .set(Lineup::getVersion, newVersion)
                    .set(Lineup::getUpdatedBy, principal.userId())
                    .set(Lineup::getUpdatedAt, now));
            if (updated == 0) {
                Lineup latest = lineupMapper.selectByMatchAndSide(matchId, resolvedSide);
                throw versionConflict(latest == null ? 0 : latest.getVersion());
            }
        }

        Map<String, Object> before = existing == null ? null : auditSnapshot(
                matchId, resolvedSide, existing, beforeSlots);
        lineupSlotMapper.deleteByLineupId(lineupId);
        lineupSlotMapper.insertBatch(slots(lineupId, formationSlots, starters, request.bench()));

        Map<String, Object> after = auditSnapshot(matchId, resolvedSide, existing,
                template.getId(), newVersion, starters, request.bench());
        auditRecorder.recordCurrent("LINEUP_SAVE", "LINEUP", lineupId, before, after);
        log.info("lineup saved, matchId={}, side={}, version={}, operator={}",
                matchId, resolvedSide, newVersion, principal.userId());
        return new LineupSaveResponse(newVersion);
    }

    /**
     * LIN-04：把当前阵容阵型复制为球队自定义模板；只写模板目录，不改变阵容版本。
     */
    @Transactional
    public Long saveAsTemplate(Long matchId, Integer side, String name) {
        AuthPrincipal principal = CurrentUser.principal();
        int resolvedSide = LineupRules.requireSide(side);
        MatchSnapshot match = requireMatch(matchId, principal.teamId());
        if (LineupRules.opponentSide(match, resolvedSide)) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "对手方阵型不可另存为模板");
        }
        String trimmed = requireName(name);
        Lineup lineup = lineupMapper.selectByMatchAndSide(matchId, resolvedSide);
        FormationTemplate base = lineup == null
                ? defaultTemplate()
                : templateMapper.selectByIdIncludingDeleted(lineup.getFormationTemplateId());
        if (base == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "阵型模板不存在");
        }

        FormationTemplate template = new FormationTemplate();
        template.setTeamId(principal.teamId());
        template.setName(trimmed);
        template.setIsPreset(CUSTOM);
        template.setBaseTemplateId(base.getId());
        template.setCreatedBy(principal.userId());
        template.setIsDeleted(0);
        templateMapper.insert(template);

        List<FormationSlot> copies = formationSlots(base.getId()).stream()
                .map(slot -> copy(slot, template.getId()))
                .toList();
        copies.forEach(formationSlotMapper::insert);
        log.info("lineup template saved, matchId={}, side={}, templateId={}, operator={}",
                matchId, resolvedSide, template.getId(), principal.userId());
        return template.getId();
    }

    private MatchSnapshot requireMatch(Long matchId, Long teamId) {
        return matchReadPort.findById(matchId)
                .filter(match -> Objects.equals(match.teamId(), teamId))
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "比赛不存在"));
    }

    private FormationTemplate requireSelectableTemplate(Long templateId, Long teamId) {
        FormationTemplate template = templateMapper.selectById(templateId);
        if (template == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "阵型模板不存在");
        }
        if (isPreset(template)) {
            return template;
        }
        if (!Objects.equals(template.getTeamId(), teamId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "阵型模板不存在");
        }
        return template;
    }

    private State loadState(MatchSnapshot match, int side) {
        Lineup lineup = lineupMapper.selectByMatchAndSide(match.id(), side);
        if (lineup == null) {
            FormationTemplate template = defaultTemplate();
            return new State(null, template, formationSlots(template.getId()), List.of());
        }
        FormationTemplate template = templateMapper
                .selectByIdIncludingDeleted(lineup.getFormationTemplateId());
        if (template == null) {
            template = defaultTemplate();
        }
        return new State(lineup, template, formationSlots(template.getId()),
                lineupSlotMapper.selectByLineupId(lineup.getId()));
    }

    private List<LineupStarterView> starterViews(State state, boolean opponent, Long teamId) {
        Map<String, LineupSlot> starterSlots = state.slots().stream()
                .filter(slot -> slot.getSlotType() == LineupSlot.STARTER)
                .collect(Collectors.toMap(LineupSlot::getSlotKey, Function.identity(),
                        (first, second) -> first));
        Map<Long, PlayerSnapshot> players = loadPlayers(state, teamId);
        return state.formationSlots().stream()
                .map(slot -> new LineupStarterView(slot.getSlotKey(),
                        opponent ? null : playerView(starterSlots.get(slot.getSlotKey()), players)))
                .toList();
    }

    private List<LineupPlayerView> benchViews(State state, boolean opponent,
                                              MatchSnapshot match, int side) {
        if (opponent) {
            return List.of();
        }
        if (state.lineup() == null) {
            return eligiblePlayers(match, side).stream()
                    .map(LineupApplicationService::playerView)
                    .toList();
        }
        Map<Long, PlayerSnapshot> players = loadPlayers(state, match.teamId());
        return state.slots().stream()
                .filter(slot -> slot.getSlotType() == LineupSlot.BENCH)
                .map(slot -> playerView(slot, players))
                .filter(Objects::nonNull)
                .toList();
    }

    private void requireEligible(MatchSnapshot match, int side,
                                 List<LineupRules.Starter> starters, List<Long> bench) {
        List<Long> playerIds = new ArrayList<>();
        starters.forEach(starter -> playerIds.add(starter.playerId()));
        playerIds.addAll(bench);
        List<Long> distinct = playerIds.stream().distinct().toList();

        Map<Long, ConfirmedRegistration> confirmed = registrationReadPort
                .findConfirmedRoster(match.id()).stream()
                .collect(Collectors.toMap(ConfirmedRegistration::playerId,
                        Function.identity(), (first, second) -> first));
        List<PlayerSnapshot> players = playerReadPort.findByIds(match.teamId(), distinct);
        Map<Long, UserAccountSnapshot> accounts = userAccountReadPort.findByIds(
                players.stream().map(PlayerSnapshot::userId).distinct().toList());
        Set<Long> activeIds = players.stream()
                .filter(player -> {
                    UserAccountSnapshot account = accounts.get(player.userId());
                    return account != null && account.status() != null
                            && account.status() == ACTIVE_ACCOUNT;
                })
                .map(PlayerSnapshot::id)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        List<Long> invalid = distinct.stream()
                .filter(playerId -> !confirmed.containsKey(playerId)
                        || !activeIds.contains(playerId))
                .toList();
        if (!invalid.isEmpty()) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "球员须为本场已确认报名且账号可用：" + join(invalid));
        }

        if (!match.internalOrTraining()) {
            return;
        }
        groupingReadPort.findSavedByMatch(match.id()).ifPresent(snapshot ->
                requireGroupMembers(snapshot, side, distinct));
    }

    private static void requireGroupMembers(SavedGroupingSnapshot snapshot, int side,
                                            List<Long> playerIds) {
        int group = side;
        List<Long> wrong = playerIds.stream()
                .filter(playerId -> !Objects.equals(snapshot.playerTeams().get(playerId), group))
                .toList();
        if (!wrong.isEmpty()) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "球员不属于本场已保存分组的 " + (group == 0 ? "A（橙队）" : "B（兰队）")
                            + " 组：" + join(wrong));
        }
    }

    private static String join(List<Long> ids) {
        return ids.stream().map(String::valueOf).collect(Collectors.joining(","));
    }

    private List<PlayerSnapshot> eligiblePlayers(MatchSnapshot match, int side) {
        List<Long> ids = registrationReadPort.findConfirmedRoster(match.id()).stream()
                .map(ConfirmedRegistration::playerId)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return List.of();
        }
        List<PlayerSnapshot> players = playerReadPort.findByIds(match.teamId(), ids);
        Map<Long, UserAccountSnapshot> accounts = userAccountReadPort.findByIds(
                players.stream().map(PlayerSnapshot::userId).distinct().toList());
        Set<Long> groupMembers = groupMembers(match, side);
        return players.stream()
                .filter(player -> {
                    UserAccountSnapshot account = accounts.get(player.userId());
                    return account != null && account.status() != null
                            && account.status() == ACTIVE_ACCOUNT;
                })
                .filter(player -> groupMembers == null || groupMembers.contains(player.id()))
                .sorted(Comparator.comparing(PlayerSnapshot::id))
                .toList();
    }

    /**
     * 仅队内/训练赛的已保存分组约束选人；无分组或草稿分组返回 null。
     */
    private Set<Long> groupMembers(MatchSnapshot match, int side) {
        if (!match.internalOrTraining()) {
            return null;
        }
        SavedGroupingSnapshot snapshot = groupingReadPort.findSavedByMatch(match.id())
                .orElse(null);
        if (snapshot == null) {
            return null;
        }
        int group = side;
        return snapshot.playerTeams().entrySet().stream()
                .filter(entry -> Objects.equals(entry.getValue(), group))
                .map(Map.Entry::getKey)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private Map<Long, PlayerSnapshot> loadPlayers(State state, Long teamId) {
        List<Long> ids = state.slots().stream()
                .map(LineupSlot::getPlayerId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return playerReadPort.findByIds(teamId, ids).stream()
                .collect(Collectors.toMap(PlayerSnapshot::id, Function.identity(),
                        (first, second) -> first));
    }

    private static LineupPlayerView playerView(LineupSlot slot,
                                               Map<Long, PlayerSnapshot> players) {
        if (slot == null || slot.getPlayerId() == null) {
            return null;
        }
        return playerView(slot.getPlayerId(), players.get(slot.getPlayerId()));
    }

    private static LineupPlayerView playerView(PlayerSnapshot player) {
        return playerView(player.id(), player);
    }

    private static LineupPlayerView playerView(Long playerId, PlayerSnapshot player) {
        if (player == null) {
            return new LineupPlayerView(playerId, "球员#" + playerId, null, -1, null);
        }
        return new LineupPlayerView(player.id(), player.name(), player.jersey(), player.position(),
                player.position() == null ? null : Position.of(player.position()).label());
    }

    private List<FormationSlot> formationSlots(Long templateId) {
        return formationSlotMapper.selectList(new LambdaQueryWrapper<FormationSlot>()
                .eq(FormationSlot::getTemplateId, templateId)
                .orderByAsc(FormationSlot::getSortOrder)
                .orderByAsc(FormationSlot::getId));
    }

    private FormationTemplate defaultTemplate() {
        FormationTemplate template = templateMapper.selectOne(
                new LambdaQueryWrapper<FormationTemplate>()
                        .eq(FormationTemplate::getIsPreset, PRESET)
                        .isNull(FormationTemplate::getTeamId)
                        .orderByAsc(FormationTemplate::getId)
                        .last("LIMIT 1"));
        if (template == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "未配置预置阵型");
        }
        return template;
    }

    private FormationTemplateView formationView(State state) {
        return new FormationTemplateView(state.template().getId(), state.template().getName(),
                isPreset(state.template()), state.template().getBaseTemplateId(),
                state.formationSlots().stream()
                        .map(slot -> new FormationSlotView(slot.getSlotKey(), slot.getRole(),
                                Position.of(slot.getRole()).label(), slot.getX(), slot.getY()))
                        .toList());
    }

    private List<LineupSlot> slots(Long lineupId, List<FormationSlot> formationSlots,
                                   List<LineupRules.Starter> starters, List<Long> bench) {
        Map<String, Long> playerBySlot = new LinkedHashMap<>();
        starters.forEach(starter -> playerBySlot.put(starter.slotKey(), starter.playerId()));
        List<LineupSlot> rows = new ArrayList<>();
        formationSlots.forEach(slot -> rows.add(slot(lineupId, slot.getSlotKey(),
                LineupSlot.STARTER, playerBySlot.get(slot.getSlotKey()),
                slot.getSortOrder() == null ? 0 : slot.getSortOrder())));
        for (int index = 0; index < bench.size(); index++) {
            rows.add(slot(lineupId, "b" + (index + 1), LineupSlot.BENCH, bench.get(index), index));
        }
        return rows;
    }

    private static LineupSlot slot(Long lineupId, String slotKey, int slotType,
                                   Long playerId, int sortOrder) {
        LineupSlot slot = new LineupSlot();
        slot.setLineupId(lineupId);
        slot.setSlotKey(slotKey);
        slot.setSlotType(slotType);
        slot.setPlayerId(playerId);
        slot.setSortOrder(sortOrder);
        slot.setIsDeleted(0);
        return slot;
    }

    private Map<String, Object> auditSnapshot(Long matchId, int side, Lineup existing,
                                              List<LineupSlot> slots) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("matchId", matchId);
        values.put("side", side);
        values.put("formationTemplateId", existing == null
                ? null : existing.getFormationTemplateId());
        values.put("version", existing == null ? 0 : existing.getVersion());
        Map<String, Long> starters = new LinkedHashMap<>();
        slots.stream()
                .filter(slot -> slot.getSlotType() == LineupSlot.STARTER)
                .forEach(slot -> starters.put(slot.getSlotKey(), slot.getPlayerId()));
        values.put("starters", starters);
        values.put("bench", slots.stream()
                .filter(slot -> slot.getSlotType() == LineupSlot.BENCH)
                .sorted(Comparator.comparing(LineupSlot::getSortOrder))
                .map(LineupSlot::getPlayerId)
                .toList());
        return values;
    }

    private Map<String, Object> auditSnapshot(Long matchId, int side, Lineup existing,
                                              Long formationTemplateId, int version,
                                              List<LineupRules.Starter> starters,
                                              List<Long> bench) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("matchId", matchId);
        values.put("side", side);
        values.put("formationTemplateId", formationTemplateId);
        values.put("version", version);
        Map<String, Long> starterMap = new LinkedHashMap<>();
        starters.forEach(starter -> starterMap.put(starter.slotKey(), starter.playerId()));
        values.put("starters", starterMap);
        values.put("bench", bench);
        return values;
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

    private static BusinessException versionConflict(int currentVersion) {
        return new BusinessException(ErrorCode.VERSION_CONFLICT,
                "阵容已被他人修改，请刷新后重试", Map.of("currentVersion", currentVersion));
    }

    private record State(Lineup lineup, FormationTemplate template,
                         List<FormationSlot> formationSlots, List<LineupSlot> slots) {
    }
}
