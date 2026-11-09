package com.yunhefc.auth.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yunhefc.auth.domain.AppRole;
import com.yunhefc.auth.domain.AppUser;
import com.yunhefc.auth.domain.InviteLink;
import com.yunhefc.auth.domain.Team;
import com.yunhefc.auth.dto.AuthDtos.InviteAdminView;
import com.yunhefc.auth.dto.AuthDtos.InviteCreateRequest;
import com.yunhefc.auth.dto.AuthDtos.InviteView;
import com.yunhefc.auth.dto.AuthDtos.MatchSummary;
import com.yunhefc.auth.dto.AuthDtos.TeamView;
import com.yunhefc.auth.dto.AuthDtos.UserView;
import com.yunhefc.auth.infrastructure.AppRoleMapper;
import com.yunhefc.auth.infrastructure.AppUserMapper;
import com.yunhefc.audit.application.AuditRecorder;
import com.yunhefc.auth.infrastructure.InviteLinkMapper;
import com.yunhefc.auth.infrastructure.InviteUseMapper;
import com.yunhefc.auth.infrastructure.TeamMapper;
import com.yunhefc.auth.infrastructure.UserRoleMapper;
import com.yunhefc.common.Labels;
import com.yunhefc.common.api.PageResponse;
import com.yunhefc.common.error.BusinessException;
import com.yunhefc.common.error.ErrorCode;
import com.yunhefc.common.security.CurrentUser;
import com.yunhefc.match.domain.TeamMatch;
import com.yunhefc.match.infrastructure.TeamMatchMapper;
import com.yunhefc.player.domain.Player;
import com.yunhefc.player.infrastructure.PlayerMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

@Service
public class InviteService {

    private final InviteLinkMapper inviteLinkMapper;
    private final InviteUseMapper inviteUseMapper;
    private final AppUserMapper userMapper;
    private final AppRoleMapper roleMapper;
    private final UserRoleMapper userRoleMapper;
    private final TeamMapper teamMapper;
    private final PlayerMapper playerMapper;
    private final TeamMatchMapper teamMatchMapper;
    private final AuditRecorder auditRecorder;
    private final String publicBaseUrl;

    public InviteService(
            InviteLinkMapper inviteLinkMapper,
            InviteUseMapper inviteUseMapper,
            AppUserMapper userMapper,
            AppRoleMapper roleMapper,
            UserRoleMapper userRoleMapper,
            TeamMapper teamMapper,
            PlayerMapper playerMapper,
            TeamMatchMapper teamMatchMapper,
            AuditRecorder auditRecorder,
            @Value("${yunhefc.public-base-url:http://localhost:5173}") String publicBaseUrl) {
        this.inviteLinkMapper = inviteLinkMapper;
        this.inviteUseMapper = inviteUseMapper;
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
        this.userRoleMapper = userRoleMapper;
        this.teamMapper = teamMapper;
        this.playerMapper = playerMapper;
        this.teamMatchMapper = teamMatchMapper;
        this.auditRecorder = auditRecorder;
        this.publicBaseUrl = publicBaseUrl;
    }

    public InviteLink findUsableForRegistration(String rawToken) {
        InviteLink invite = findByToken(rawToken);
        if (!isUsable(invite)) {
            throw new BusinessException(ErrorCode.INVITE_UNAVAILABLE);
        }
        return invite;
    }

    public InviteView resolve(String rawToken) {
        InviteLink invite = findByToken(rawToken);
        if (invite == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "邀请不存在");
        }
        return toView(invite);
    }

    @Transactional
    public void consume(InviteLink invite, Long userId) {
        if (inviteLinkMapper.incrementUsedCount(invite.getId()) == 0) {
            throw new BusinessException(ErrorCode.INVITE_UNAVAILABLE);
        }
        inviteUseMapper.upsert(invite.getId(), userId);
    }

    @Transactional
    public InviteView accept(String rawToken, Long userId) {
        InviteLink invite = findUsableForRegistration(rawToken);
        AppUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED);
        }
        if (user.getStatus() == 2) {
            throw new BusinessException(ErrorCode.ACCOUNT_DISABLED);
        }
        Player player = playerMapper.selectOne(new LambdaQueryWrapper<Player>()
                .eq(Player::getUserId, userId)
                .last("LIMIT 1"));
        if (player == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "球员档案不存在");
        }

        if (invite.getType() == 1) {
            consume(invite, userId);
            InviteView view = toView(invite);
            auditRecorder.record(userId, "INVITE_ACCEPT", "INVITE", invite.getId(), null, view);
            return view;
        }

        if (player.getType() == 1) {
            return toView(invite);
        }

        user.setStatus(1);
        userMapper.updateById(user);
        player.setType(1);
        playerMapper.updateById(player);
        userRoleMapper.removeAll(userId);
        AppRole playerRole = role(invite.getTeamId(), "PLAYER");
        if (playerRole == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "PLAYER 角色未初始化");
        }
        userRoleMapper.upsert(userId, playerRole.getId());
        consume(invite, userId);
        InviteView view = toView(invite);
        auditRecorder.record(userId, "INVITE_ACCEPT", "INVITE", invite.getId(),
                java.util.Map.of("status", 0), java.util.Map.of("status", 1, "playerType", 1));
        return view;
    }

    @Transactional
    public InviteAdminView create(Long inviterUserId, InviteCreateRequest request) {
        int type = request.type() == null ? 0 : request.type();
        if (type != 0 && type != 1) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "邀请类型不合法");
        }
        if (type == 1 && request.matchId() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "比赛邀请必须指定比赛");
        }
        if (type == 0 && request.matchId() != null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "入队邀请不能指定比赛");
        }
        Long teamId = teamId();
        if (type == 1 && teamMatchMapper.selectById(request.matchId()) == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "比赛不存在");
        }
        InviteAdminView view = createInternal(teamId, type, request.matchId(), inviterUserId,
                request.expiresInDays(), request.maxUses());
        auditRecorder.record(inviterUserId, "INVITE_CREATE", "INVITE", view.id(), null,
                java.util.Map.of("type", view.type(), "expiresAt", view.expiresAt(),
                        "maxUses", view.maxUses() == null ? "" : view.maxUses()));
        return view;
    }

    @Transactional
    public InviteAdminView createMatchInvite(Long inviterUserId, Long matchId, Integer expiresInDays, Integer maxUses) {
        TeamMatch match = teamMatchMapper.selectById(matchId);
        if (match == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "比赛不存在");
        }
        InviteAdminView view = createInternal(match.getTeamId(), 1, matchId, inviterUserId, expiresInDays, maxUses);
        auditRecorder.record(inviterUserId, "INVITE_CREATE", "INVITE", view.id(), null,
                java.util.Map.of("type", view.type(), "expiresAt", view.expiresAt(),
                        "maxUses", view.maxUses() == null ? "" : view.maxUses()));
        return view;
    }

    public PageResponse<InviteAdminView> list(Integer type, Integer status, long page, long pageSize) {
        Page<InviteLink> result = inviteLinkMapper.selectPage(new Page<>(page, pageSize),
                new LambdaQueryWrapper<InviteLink>()
                        .eq(type != null, InviteLink::getType, type)
                        .eq(status != null, InviteLink::getStatus, status)
                        .orderByDesc(InviteLink::getId));
        return PageResponse.of(result.getRecords().stream().map(this::toAdminView).toList(),
                result.getTotal(), page, pageSize);
    }

    public void revoke(Long id) {
        InviteLink invite = inviteLinkMapper.selectById(id);
        if (invite == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        invite.setStatus(1);
        invite.setRevokedAt(LocalDateTime.now());
        inviteLinkMapper.updateById(invite);
        auditRecorder.record(CurrentUser.principal().userId(), "INVITE_REVOKE", "INVITE", id,
                java.util.Map.of("status", 0), java.util.Map.of("status", 1));
    }

    private InviteAdminView createInternal(Long teamId, int type, Long matchId, Long inviterUserId,
                                           Integer expiresInDays, Integer maxUses) {
        int days = expiresInDays == null ? 7 : expiresInDays;
        if (days < 1 || days > 30) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "有效期必须为 1–30 天");
        }
        if (maxUses != null && maxUses < 1) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "使用次数上限必须大于 0");
        }
        String rawToken = UUID.randomUUID().toString().replace("-", "");
        InviteLink invite = new InviteLink();
        invite.setTeamId(teamId);
        invite.setType(type);
        invite.setTokenHash(sha256(rawToken));
        invite.setMatchId(matchId);
        invite.setInviterUserId(inviterUserId);
        invite.setExpiresAt(LocalDateTime.now().plusDays(days));
        invite.setMaxUses(maxUses);
        invite.setUsedCount(0);
        invite.setStatus(0);
        invite.setCreatedAt(LocalDateTime.now());
        invite.setUpdatedAt(LocalDateTime.now());
        inviteLinkMapper.insert(invite);
        return toAdminView(invite, rawToken);
    }

    private InviteLink findByToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "邀请不存在");
        }
        return inviteLinkMapper.findByTokenHash(sha256(rawToken));
    }

    private boolean isUsable(InviteLink invite) {
        if (invite == null || invite.getStatus() != 0 || invite.getExpiresAt().isBefore(LocalDateTime.now())) {
            return false;
        }
        return invite.getMaxUses() == null || invite.getUsedCount() < invite.getMaxUses();
    }

    private InviteView toView(InviteLink invite) {
        Team team = teamMapper.selectById(invite.getTeamId());
        TeamMatch match = invite.getMatchId() == null ? null : teamMatchMapper.selectById(invite.getMatchId());
        AppUser inviter = userMapper.selectById(invite.getInviterUserId());
        int effectiveStatus = invite.getStatus();
        if (effectiveStatus == 0 && invite.getExpiresAt().isBefore(LocalDateTime.now())) {
            effectiveStatus = 2;
        }
        boolean exhausted = invite.getMaxUses() != null && invite.getUsedCount() >= invite.getMaxUses();
        Integer remaining = invite.getMaxUses() == null ? null : Math.max(0, invite.getMaxUses() - invite.getUsedCount());
        return new InviteView(
                invite.getType(),
                team == null ? null : new TeamView(team.getId(), team.getName(), team.getShortName()),
                match == null ? null : new MatchSummary(match.getId(), match.getTitle(), match.getStartAt(),
                        match.getEndAt(), match.getVenue(), match.getStatus(), match.getHomeTeamName(),
                        match.getAwayTeamName()),
                inviter == null ? null : inviterView(inviter),
                invite.getExpiresAt(),
                invite.getMaxUses(),
                invite.getUsedCount(),
                remaining,
                effectiveStatus,
                Labels.inviteStatus(effectiveStatus),
                exhausted);
    }

    private InviteAdminView toAdminView(InviteLink invite) {
        return toAdminView(invite, null);
    }

    private InviteAdminView toAdminView(InviteLink invite, String rawToken) {
        InviteView view = toView(invite);
        return new InviteAdminView(
                invite.getId(),
                view.type(),
                Labels.inviteType(view.type()),
                view.team(),
                view.match(),
                view.inviter(),
                view.expiresAt(),
                view.maxUses(),
                view.usedCount(),
                view.remainingUses(),
                view.status(),
                view.statusLabel(),
                rawToken == null ? null : publicBaseUrl + "/invite.html?token=" + rawToken,
                invite.getCreatedAt());
    }

    private UserView inviterView(AppUser user) {
        return new UserView(user.getId(), user.getPhone(), user.getStatus(), Labels.userStatus(user.getStatus()),
                new LinkedHashSet<>(userRoleMapper.findRoleCodes(user.getId())));
    }

    private AppRole role(Long teamId, String code) {
        return roleMapper.selectOne(new LambdaQueryWrapper<AppRole>()
                .eq(AppRole::getTeamId, teamId)
                .eq(AppRole::getCode, code));
    }

    private Long teamId() {
        Team team = teamMapper.selectOne(new LambdaQueryWrapper<Team>().orderByAsc(Team::getId).last("LIMIT 1"));
        if (team == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "球队未初始化");
        }
        return team.getId();
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
