package com.yunhefc.grouping.application;

import com.yunhefc.audit.application.AuditRecorder;
import com.yunhefc.common.error.BusinessException;
import com.yunhefc.common.error.ErrorCode;
import com.yunhefc.common.security.CurrentUser;
import com.yunhefc.contracts.match.MatchReadPort;
import com.yunhefc.contracts.match.MatchSnapshot;
import com.yunhefc.contracts.player.PlayerReadPort;
import com.yunhefc.contracts.player.PlayerSnapshot;
import com.yunhefc.contracts.registration.ConfirmedRegistration;
import com.yunhefc.contracts.registration.RegistrationReadPort;
import com.yunhefc.contracts.security.AuthPrincipal;
import com.yunhefc.contracts.security.UserAccountReadPort;
import com.yunhefc.contracts.security.UserAccountSnapshot;
import com.yunhefc.contracts.settings.GroupingSettingsPort;
import com.yunhefc.grouping.domain.AssignedPlayer;
import com.yunhefc.grouping.domain.GroupingAlgorithm;
import com.yunhefc.grouping.domain.GroupingAssignment;
import com.yunhefc.grouping.domain.GroupingCandidate;
import com.yunhefc.grouping.domain.GroupingCandidates;
import com.yunhefc.grouping.domain.GroupingPlayerStats;
import com.yunhefc.grouping.domain.GroupingRandom;
import com.yunhefc.grouping.domain.GroupingScore;
import com.yunhefc.grouping.domain.GroupingScoreCalculator;
import com.yunhefc.grouping.domain.GroupingWarnings;
import com.yunhefc.grouping.domain.GroupingWeights;
import com.yunhefc.grouping.domain.MatchGrouping;
import com.yunhefc.grouping.domain.MatchGroupingPlayer;
import com.yunhefc.grouping.domain.RosterFingerprint;
import com.yunhefc.grouping.dto.GroupingDtos.GroupedPlayerView;
import com.yunhefc.grouping.dto.GroupingDtos.GroupingAdjustRequest;
import com.yunhefc.grouping.dto.GroupingDtos.GroupingAdminView;
import com.yunhefc.grouping.dto.GroupingDtos.GroupingClearResponse;
import com.yunhefc.grouping.dto.GroupingDtos.GroupingRosterDiffView;
import com.yunhefc.grouping.dto.GroupingDtos.GroupingSaveRequest;
import com.yunhefc.grouping.dto.GroupingDtos.GroupingSaveResponse;
import com.yunhefc.grouping.dto.GroupingDtos.GroupingScoresView;
import com.yunhefc.grouping.dto.GroupingDtos.GroupingSnapshotView;
import com.yunhefc.grouping.dto.GroupingDtos.GroupingTeamsView;
import com.yunhefc.grouping.dto.GroupingDtos.GroupingView;
import com.yunhefc.grouping.dto.GroupingDtos.GroupingWeightsView;
import com.yunhefc.grouping.infrastructure.MatchGroupingMapper;
import com.yunhefc.grouping.infrastructure.MatchGroupingPlayerMapper;
import com.yunhefc.stats.application.PlayerStatsRow;
import com.yunhefc.stats.application.StatsQueryService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
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
public class GroupingApplicationService {

    private static final Logger log = LoggerFactory.getLogger(GroupingApplicationService.class);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int LOCKED = 2;
    private static final int TEAM_A = 0;
    private static final int TEAM_B = 1;
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);

    private final MatchGroupingMapper groupingMapper;
    private final MatchGroupingPlayerMapper groupingPlayerMapper;
    private final MatchReadPort matchReadPort;
    private final RegistrationReadPort registrationReadPort;
    private final PlayerReadPort playerReadPort;
    private final UserAccountReadPort userAccountReadPort;
    private final StatsQueryService statsQueryService;
    private final GroupingSettingsPort groupingSettingsPort;
    private final AuditRecorder auditRecorder;
    private final Clock clock;

    @Transactional(readOnly = true)
    public GroupingView viewForPlayer(Long matchId) {
        ReadModel model = readModel(matchId);
        return new GroupingView(model.teams(), model.weights(), model.diff(),
                model.warnings(), model.savedAt());
    }

    @Transactional(readOnly = true)
    public GroupingAdminView adminView(Long matchId) {
        ReadModel model = readModel(matchId);
        return new GroupingAdminView(model.teams(), model.weights(), model.diff(),
                model.warnings(), model.savedAt(), model.scores(), model.rosterFingerprint(),
                model.generatedAt(), model.ungroupedPlayerIds(), model.rosterDiff());
    }

    /**
     * GRP-03：仅锁定状态的队内/训练赛；候选不足、覆盖确认与状态门按固定优先级校验。
     */
    @Transactional
    public GroupingAdminView generate(Long matchId, Boolean confirm) {
        AuthPrincipal principal = CurrentUser.principal();
        MatchSnapshot match = lockGroupingMatch(matchId);

        List<GroupingCandidate> candidates = loadCandidates(match);
        if (candidates.size() < 2) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "确认报名球员不足 2 人，无法分组");
        }

        MatchGrouping existing = groupingMapper.selectByMatchIncludingDeleted(matchId);
        boolean visible = visible(existing);
        if (visible && !Boolean.TRUE.equals(confirm)) {
            throw new BusinessException(ErrorCode.CONFIRM_REQUIRED, "已有分组，重新生成需要确认");
        }
        List<MatchGroupingPlayer> existingPlayers = visible
                ? groupingPlayerMapper.selectByGroupingId(existing.getId()) : List.of();
        Map<String, Object> before = visible ? auditSnapshot(existing, existingPlayers) : null;

        GroupingWeights weights = GroupingWeights.resolve(groupingSettingsPort.load(match.teamId()));
        String season = match.effectiveSeason();
        Map<Long, GroupingPlayerStats> stats = loadStats(match.teamId(), season);
        int finishedMatches = matchReadPort.countFinishedInSeason(match.teamId(), season);
        List<GroupingScore> scores = GroupingScoreCalculator.calculate(candidates, stats, weights,
                finishedMatches);
        Map<Long, GroupingScore> scoreByPlayer = scores.stream()
                .collect(Collectors.toMap(GroupingScore::playerId, Function.identity()));
        List<AssignedPlayer> players = candidates.stream()
                .map(candidate -> new AssignedPlayer(candidate, scoreByPlayer.get(candidate.playerId())))
                .toList();

        long seed = SECURE_RANDOM.nextLong();
        log.debug("grouping generate matchId={} seed={}", matchId, seed);
        GroupingAssignment assignment = GroupingAlgorithm.assign(players, GroupingRandom.seeded(seed));
        List<String> warnings = GroupingWarnings.compute(
                assignment.teamA(), assignment.teamB(), candidates);
        String fingerprint = RosterFingerprint.of(candidateIds(candidates));
        Instant now = Instant.now(clock);

        MatchGrouping grouping = persist(existing, matchId, weights, fingerprint, now,
                principal.userId(), assignment);
        List<MatchGroupingPlayer> rows = groupingPlayerMapper.selectByGroupingId(grouping.getId());
        auditRecorder.recordCurrent("grouping.generate", "team_match", matchId,
                before, auditSnapshot(grouping, rows));
        log.info("grouping generated, matchId={}, candidates={}, warnings={}, operator={}",
                matchId, candidates.size(), warnings.size(), principal.userId());

        BigDecimal sumA = sum(assignment.teamA());
        BigDecimal sumB = sum(assignment.teamB());
        return new GroupingAdminView(
                new GroupingTeamsView(teamView(assignment.teamA()), teamView(assignment.teamB())),
                weightsView(weights),
                sumA.subtract(sumB).abs().setScale(2, RoundingMode.HALF_UP),
                warnings,
                null,
                new GroupingScoresView(sumA, sumB,
                        sumA.subtract(sumB).abs().setScale(2, RoundingMode.HALF_UP)),
                fingerprint,
                now,
                List.of(),
                new GroupingRosterDiffView(false, List.of(), List.of()));
    }

    /**
     * GRP-04：玩家手动划分必须覆盖当前候选池；名单变化或客户端指纹过期时拒绝，
     * 已保存分组被调整后回到草稿。
     */
    @Transactional
    public GroupingAdminView adjust(Long matchId, GroupingAdjustRequest request) {
        MatchSnapshot match = lockGroupingMatch(matchId);
        MatchGrouping grouping = groupingMapper.selectByMatchIncludingDeleted(matchId);
        if (!visible(grouping)) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "尚未生成分组");
        }
        List<MatchGroupingPlayer> rows = groupingPlayerMapper.selectByGroupingId(grouping.getId());
        List<GroupingCandidate> candidates = loadCandidates(match);
        List<Long> candidateIds = candidateIds(candidates);
        String currentFingerprint = RosterFingerprint.of(candidateIds);
        if (!Objects.equals(currentFingerprint, grouping.getRosterFingerprint())
                || !Objects.equals(currentFingerprint, request.expectedFingerprint())) {
            throw new BusinessException(ErrorCode.GROUPING_FINGERPRINT_CONFLICT,
                    "分组名单已变化，请重新生成分组");
        }

        List<Long> teamAIds = request.teams().teamA();
        List<Long> teamBIds = request.teams().teamB();
        validatePartition(candidateIds, teamAIds, teamBIds);

        Map<Long, Integer> currentTeams = rows.stream().collect(Collectors.toMap(
                MatchGroupingPlayer::getPlayerId, MatchGroupingPlayer::getTeam));
        if (currentTeams.size() != candidateIds.size()
                || !currentTeams.keySet().containsAll(candidateIds)) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "分组数据与当前名单不一致，请重新生成分组");
        }
        boolean unchanged = teamAIds.stream().allMatch(id -> currentTeams.get(id) == TEAM_A)
                && teamBIds.stream().allMatch(id -> currentTeams.get(id) == TEAM_B);
        if (unchanged) {
            return adminView(matchId);
        }
        if (grouping.getSavedAt() != null && !Boolean.TRUE.equals(request.confirm())) {
            throw new BusinessException(ErrorCode.CONFIRM_REQUIRED,
                    "已保存的分组调整后需要确认");
        }

        Map<String, Object> before = auditSnapshot(grouping, rows);
        groupingMapper.resetSavedAt(grouping.getId());
        grouping.setSavedAt(null);
        updateTeams(grouping.getId(), currentTeams, teamAIds, teamBIds);
        List<MatchGroupingPlayer> newRows = groupingPlayerMapper.selectByGroupingId(grouping.getId());
        auditRecorder.recordCurrent("grouping.adjust", "team_match", matchId,
                before, auditSnapshot(grouping, newRows));
        log.info("grouping adjusted, matchId={}, operator={}",
                matchId, CurrentUser.principal().userId());
        return adminView(matchId);
    }

    /**
     * GRP-05：保存只写 saved_at，保留生成时指纹；名单变化与均衡警告均需确认。
     */
    @Transactional
    public GroupingSaveResponse save(Long matchId, GroupingSaveRequest request) {
        MatchSnapshot match = lockGroupingMatch(matchId);
        MatchGrouping grouping = groupingMapper.selectByMatchIncludingDeleted(matchId);
        if (!visible(grouping)) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "尚未生成分组");
        }
        if (!Objects.equals(grouping.getRosterFingerprint(), request.expectedFingerprint())) {
            throw new BusinessException(ErrorCode.GROUPING_FINGERPRINT_CONFLICT,
                    "分组名单已变化，请重新生成分组");
        }

        List<MatchGroupingPlayer> rows = groupingPlayerMapper.selectByGroupingId(grouping.getId());
        List<GroupingCandidate> candidates = loadCandidates(match);
        String currentFingerprint = RosterFingerprint.of(candidateIds(candidates));
        boolean rosterChanged = !Objects.equals(currentFingerprint, grouping.getRosterFingerprint());
        Map<Long, PlayerSnapshot> players = loadPlayers(match.teamId(),
                rows.stream().map(MatchGroupingPlayer::getPlayerId).toList());
        List<String> warnings = GroupingWarnings.compute(
                toAssigned(rows.stream().filter(row -> row.getTeam() == TEAM_A).toList(), players),
                toAssigned(rows.stream().filter(row -> row.getTeam() == TEAM_B).toList(), players),
                candidates);
        if ((rosterChanged || !warnings.isEmpty()) && !Boolean.TRUE.equals(request.confirm())) {
            throw new BusinessException(ErrorCode.CONFIRM_REQUIRED,
                    rosterChanged ? "名单已变化，保存需要确认" : "分组存在警告，保存需要确认");
        }

        Map<String, Object> before = auditSnapshot(grouping, rows);
        Instant now = Instant.now(clock);
        groupingMapper.markSaved(grouping.getId(), now);
        grouping.setSavedAt(now);
        auditRecorder.recordCurrent("grouping.save", "team_match", matchId,
                before, auditSnapshot(grouping, rows));
        log.info("grouping saved, matchId={}, operator={}",
                matchId, CurrentUser.principal().userId());
        return new GroupingSaveResponse(now, new GroupingSnapshotView(
                now, grouping.getRosterFingerprint(), toTeamsView(rows, players)));
    }

    /**
     * GRP-06：物理删除成员行与主行；无分组时幂等成功且不写审计。
     */
    @Transactional
    public GroupingClearResponse clear(Long matchId) {
        lockGroupingMatch(matchId);
        MatchGrouping grouping = groupingMapper.selectByMatchIncludingDeleted(matchId);
        if (grouping == null) {
            return new GroupingClearResponse(true);
        }
        List<MatchGroupingPlayer> rows = groupingPlayerMapper.selectByGroupingId(grouping.getId());
        Map<String, Object> before = visible(grouping) ? auditSnapshot(grouping, rows) : null;
        groupingPlayerMapper.deleteByGroupingId(grouping.getId());
        groupingMapper.deletePhysical(grouping.getId());
        if (before != null) {
            auditRecorder.recordCurrent("grouping.clear", "team_match", matchId, before, null);
            log.info("grouping cleared, matchId={}, operator={}",
                    matchId, CurrentUser.principal().userId());
        }
        return new GroupingClearResponse(true);
    }

    private MatchSnapshot lockGroupingMatch(Long matchId) {
        AuthPrincipal principal = CurrentUser.principal();
        MatchSnapshot match = matchReadPort.lockForGrouping(matchId, principal.teamId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "比赛不存在"));
        requireInternal(match);
        if (match.status() == null || match.status() != LOCKED) {
            throw new BusinessException(ErrorCode.MATCH_STATE_CONFLICT,
                    "仅已锁定的比赛可以操作分组");
        }
        return match;
    }

    private static void validatePartition(List<Long> candidateIds,
                                          List<Long> teamAIds, List<Long> teamBIds) {
        Set<Long> candidates = new HashSet<>(candidateIds);
        Set<Long> seen = new HashSet<>();
        for (List<Long> team : List.of(teamAIds, teamBIds)) {
            for (Long playerId : team) {
                if (playerId == null || !candidates.contains(playerId)) {
                    throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                            "分组包含未知或非候选球员");
                }
                if (!seen.add(playerId)) {
                    throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                            "分组存在重复球员");
                }
            }
        }
        if (seen.size() != candidates.size()) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "分组必须覆盖当前候选池的全部球员");
        }
    }

    private void updateTeams(Long groupingId, Map<Long, Integer> currentTeams,
                             List<Long> teamAIds, List<Long> teamBIds) {
        teamAIds.stream()
                .filter(playerId -> currentTeams.get(playerId) != TEAM_A)
                .forEach(playerId -> groupingPlayerMapper.updateTeam(
                        groupingId, playerId, TEAM_A));
        teamBIds.stream()
                .filter(playerId -> currentTeams.get(playerId) != TEAM_B)
                .forEach(playerId -> groupingPlayerMapper.updateTeam(
                        groupingId, playerId, TEAM_B));
    }

    private MatchGrouping persist(MatchGrouping existing, Long matchId, GroupingWeights weights,
                                  String fingerprint, Instant now, Long userId,
                                  GroupingAssignment assignment) {
        MatchGrouping grouping = existing == null ? new MatchGrouping() : existing;
        if (existing == null) {
            grouping.setMatchId(matchId);
            grouping.setCreatedBy(userId);
            grouping.setIsDeleted(0);
        } else {
            groupingMapper.overwrite(existing.getId(), weights.manualWeight(), weights.autoWeight(),
                    fingerprint, now, userId);
        }
        grouping.setManualWeight(weights.manualWeight());
        grouping.setAutoWeight(weights.autoWeight());
        grouping.setRosterFingerprint(fingerprint);
        grouping.setGeneratedAt(now);
        grouping.setSavedAt(null);
        grouping.setIsDeleted(0);
        grouping.setCreatedBy(userId);
        if (existing == null) {
            groupingMapper.insert(grouping);
        }

        groupingPlayerMapper.deleteByGroupingId(grouping.getId());
        List<MatchGroupingPlayer> rows = new ArrayList<>();
        assignment.teamA().forEach(player -> rows.add(toRow(grouping.getId(), player, TEAM_A)));
        assignment.teamB().forEach(player -> rows.add(toRow(grouping.getId(), player, TEAM_B)));
        if (!rows.isEmpty()) {
            groupingPlayerMapper.insertBatch(rows);
        }
        return grouping;
    }

    private ReadModel readModel(Long matchId) {
        AuthPrincipal principal = CurrentUser.principal();
        MatchSnapshot match = matchReadPort.findById(matchId)
                .filter(item -> Objects.equals(item.teamId(), principal.teamId()))
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "比赛不存在"));
        requireInternal(match);
        List<GroupingCandidate> candidates = loadCandidates(match);
        GroupingWeights weights = GroupingWeights.resolve(groupingSettingsPort.load(match.teamId()));

        MatchGrouping grouping = groupingMapper.selectByMatchIncludingDeleted(matchId);
        boolean visible = visible(grouping);
        List<MatchGroupingPlayer> rows = visible
                ? groupingPlayerMapper.selectByGroupingId(grouping.getId()) : List.of();
        Map<Long, PlayerSnapshot> players = loadPlayers(match.teamId(),
                rows.stream().map(MatchGroupingPlayer::getPlayerId).toList());
        List<Long> candidateIds = candidateIds(candidates);
        Set<Long> groupedIds = rows.stream().map(MatchGroupingPlayer::getPlayerId)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        List<Long> ungrouped = candidateIds.stream()
                .filter(id -> !groupedIds.contains(id))
                .toList();
        List<Long> added = visible ? ungrouped : List.of();
        List<Long> removed = visible
                ? groupedIds.stream().filter(id -> !candidateIds.contains(id)).sorted().toList()
                : List.of();
        String currentFingerprint = RosterFingerprint.of(candidateIds);
        boolean changed = visible && !currentFingerprint.equals(grouping.getRosterFingerprint());

        List<AssignedPlayer> teamA = toAssigned(rows.stream()
                .filter(row -> row.getTeam() == TEAM_A).toList(), players);
        List<AssignedPlayer> teamB = toAssigned(rows.stream()
                .filter(row -> row.getTeam() == TEAM_B).toList(), players);
        BigDecimal sumA = sum(teamA);
        BigDecimal sumB = sum(teamB);
        BigDecimal diff = sumA.subtract(sumB).abs().setScale(2, RoundingMode.HALF_UP);

        return new ReadModel(
                visible ? toTeamsView(rows, players) : emptyTeams(),
                weightsView(weights),
                visible ? diff : ZERO,
                visible ? GroupingWarnings.compute(teamA, teamB, candidates) : List.of(),
                visible ? grouping.getSavedAt() : null,
                visible ? new GroupingScoresView(sumA, sumB, diff)
                        : new GroupingScoresView(ZERO, ZERO, ZERO),
                visible ? grouping.getRosterFingerprint() : null,
                visible ? grouping.getGeneratedAt() : null,
                ungrouped,
                visible
                        ? new GroupingRosterDiffView(changed, added, removed)
                        : new GroupingRosterDiffView(false, List.of(), List.of()));
    }

    private List<GroupingCandidate> loadCandidates(MatchSnapshot match) {
        List<ConfirmedRegistration> registrations =
                registrationReadPort.findConfirmedRoster(match.id());
        if (registrations.isEmpty()) {
            return List.of();
        }
        List<Long> playerIds = registrations.stream()
                .map(ConfirmedRegistration::playerId)
                .distinct()
                .toList();
        List<PlayerSnapshot> players = playerReadPort.findByIds(match.teamId(), playerIds);
        Map<Long, UserAccountSnapshot> accounts = userAccountReadPort.findByIds(
                players.stream().map(PlayerSnapshot::userId).distinct().toList());
        return GroupingCandidates.select(match.guestsIncluded(), registrations, players, accounts);
    }

    private Map<Long, GroupingPlayerStats> loadStats(Long teamId, String season) {
        Map<Long, PlayerStatsRow> rows = statsQueryService.seasonStats(teamId,
                season == null ? "" : season);
        Map<Long, GroupingPlayerStats> stats = new HashMap<>();
        rows.forEach((playerId, row) -> stats.put(playerId,
                new GroupingPlayerStats(row.appearances(), row.goals(), row.assists())));
        return stats;
    }

    private Map<Long, PlayerSnapshot> loadPlayers(Long teamId, List<Long> playerIds) {
        if (playerIds.isEmpty()) {
            return Map.of();
        }
        return playerReadPort.findByIds(teamId, playerIds.stream().distinct().toList()).stream()
                .collect(Collectors.toMap(PlayerSnapshot::id, Function.identity()));
    }

    private static GroupingTeamsView toTeamsView(List<MatchGroupingPlayer> rows,
                                                 Map<Long, PlayerSnapshot> players) {
        return new GroupingTeamsView(
                rows.stream().filter(row -> row.getTeam() == TEAM_A)
                        .sorted(Comparator.comparing(MatchGroupingPlayer::getPlayerId))
                        .map(row -> toPlayerView(row, players.get(row.getPlayerId())))
                        .toList(),
                rows.stream().filter(row -> row.getTeam() == TEAM_B)
                        .sorted(Comparator.comparing(MatchGroupingPlayer::getPlayerId))
                        .map(row -> toPlayerView(row, players.get(row.getPlayerId())))
                        .toList());
    }

    private static GroupedPlayerView toPlayerView(MatchGroupingPlayer row, PlayerSnapshot player) {
        return new GroupedPlayerView(
                row.getPlayerId(),
                player == null ? "球员#" + row.getPlayerId() : player.name(),
                player == null ? null : player.jersey(),
                player == null ? -1 : player.position(),
                row.getManualScore(),
                row.getAutoScore(),
                row.getFinalScore());
    }

    private static List<GroupedPlayerView> teamView(List<AssignedPlayer> team) {
        return team.stream()
                .sorted(Comparator.comparing(player -> player.candidate().playerId()))
                .map(player -> new GroupedPlayerView(
                        player.candidate().playerId(),
                        player.candidate().name(),
                        player.candidate().jersey(),
                        player.candidate().position(),
                        player.score().manualScore(),
                        player.score().autoScore(),
                        player.score().finalScore()))
                .toList();
    }

    private static List<AssignedPlayer> toAssigned(List<MatchGroupingPlayer> rows,
                                                   Map<Long, PlayerSnapshot> players) {
        return rows.stream().map(row -> {
            PlayerSnapshot player = players.get(row.getPlayerId());
            GroupingCandidate candidate = player == null
                    ? new GroupingCandidate(row.getPlayerId(), "球员#" + row.getPlayerId(),
                            null, -1, 3)
                    : new GroupingCandidate(player.id(), player.name(), player.jersey(),
                            player.position(), player.skill() == null ? 3 : player.skill());
            GroupingScore score = new GroupingScore(row.getPlayerId(), row.getManualScore(),
                    row.getAutoScore(),
                    row.getFinalScore() == null ? ZERO : row.getFinalScore(),
                    row.getAutoScore() == null);
            return new AssignedPlayer(candidate, score);
        }).toList();
    }

    private static GroupingTeamsView emptyTeams() {
        return new GroupingTeamsView(List.of(), List.of());
    }

    private static GroupingWeightsView weightsView(GroupingWeights weights) {
        return new GroupingWeightsView(weights.manualWeight(), weights.autoWeight(),
                weights.appearanceWeight(), weights.goalWeight(), weights.assistWeight(),
                weights.minAppearances());
    }

    private static MatchGroupingPlayer toRow(Long groupingId, AssignedPlayer player, int team) {
        MatchGroupingPlayer row = new MatchGroupingPlayer();
        row.setGroupingId(groupingId);
        row.setPlayerId(player.candidate().playerId());
        row.setTeam(team);
        row.setManualScore(player.score().manualScore());
        row.setAutoScore(player.score().autoScore());
        row.setFinalScore(player.score().finalScore());
        row.setIsDeleted(0);
        return row;
    }

    private static BigDecimal sum(List<AssignedPlayer> team) {
        return team.stream()
                .map(player -> player.score().finalScore())
                .filter(Objects::nonNull)
                .reduce(ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private static List<Long> candidateIds(List<GroupingCandidate> candidates) {
        return candidates.stream().map(GroupingCandidate::playerId).distinct().sorted().toList();
    }

    private static boolean visible(MatchGrouping grouping) {
        return grouping != null && (grouping.getIsDeleted() == null || grouping.getIsDeleted() == 0);
    }

    private static void requireInternal(MatchSnapshot match) {
        if (!match.internalOrTraining()) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "仅队内赛/训练赛支持分组");
        }
    }

    private static Map<String, Object> auditSnapshot(MatchGrouping grouping,
                                                     List<MatchGroupingPlayer> rows) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("matchId", grouping.getMatchId());
        values.put("manualWeight", grouping.getManualWeight());
        values.put("autoWeight", grouping.getAutoWeight());
        values.put("rosterFingerprint", grouping.getRosterFingerprint());
        values.put("generatedAt", grouping.getGeneratedAt());
        values.put("savedAt", grouping.getSavedAt());
        values.put("players", rows.stream().map(row -> {
            Map<String, Object> player = new LinkedHashMap<>();
            player.put("playerId", row.getPlayerId());
            player.put("team", row.getTeam());
            player.put("manualScore", row.getManualScore());
            player.put("autoScore", row.getAutoScore());
            player.put("finalScore", row.getFinalScore());
            return player;
        }).toList());
        return values;
    }

    private record ReadModel(
            GroupingTeamsView teams,
            GroupingWeightsView weights,
            BigDecimal diff,
            List<String> warnings,
            Instant savedAt,
            GroupingScoresView scores,
            String rosterFingerprint,
            Instant generatedAt,
            List<Long> ungroupedPlayerIds,
            GroupingRosterDiffView rosterDiff) {
    }
}
