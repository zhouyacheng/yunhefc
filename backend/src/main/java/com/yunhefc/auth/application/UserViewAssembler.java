package com.yunhefc.auth.application;

import com.yunhefc.auth.domain.AppUser;
import com.yunhefc.auth.dto.AuthDtos.UserSummary;
import com.yunhefc.auth.dto.AuthDtos.UserView;
import com.yunhefc.auth.infrastructure.UserRoleMapper;
import com.yunhefc.auth.infrastructure.UserRoleRow;
import com.yunhefc.contracts.player.PlayerReadPort;
import com.yunhefc.contracts.player.PlayerSnapshot;
import com.yunhefc.contracts.player.PlayerType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class UserViewAssembler {

    private final PlayerReadPort playerReadPort;
    private final UserRoleMapper userRoleMapper;

    public UserView toView(AppUser user) {
        PlayerSnapshot player = playerReadPort.findByUserId(user.getId()).orElse(null);
        String phoneMasked = maskPhone(user.getPhone());
        return new UserView(
                user.getId(),
                player == null ? phoneMasked : player.name(),
                phoneMasked,
                user.getStatus().code(),
                user.getStatus().label(),
                new LinkedHashSet<>(userRoleMapper.findRoleCodes(user.getId())),
                user.getCreatedAt(),
                user.isWechatBound());
    }

    public UserSummary toSummary(AppUser user) {
        PlayerSnapshot player = playerReadPort.findByUserId(user.getId()).orElse(null);
        return toSummary(user, player, new LinkedHashSet<>(userRoleMapper.findRoleCodes(user.getId())));
    }

    public Map<Long, UserSummary> toSummaries(List<AppUser> users) {
        if (users.isEmpty()) {
            return Map.of();
        }
        List<Long> userIds = users.stream().map(AppUser::getId).toList();
        Map<Long, PlayerSnapshot> players = new HashMap<>();
        for (PlayerSnapshot player : playerReadPort.findByUserIds(userIds)) {
            players.put(player.userId(), player);
        }
        Map<Long, Set<String>> roles = new HashMap<>();
        for (UserRoleRow row : userRoleMapper.findRoleCodesByUserIds(userIds)) {
            roles.computeIfAbsent(row.getUserId(), id -> new LinkedHashSet<>()).add(row.getCode());
        }
        Map<Long, UserSummary> summaries = new HashMap<>();
        for (AppUser user : users) {
            summaries.put(user.getId(), toSummary(
                    user, players.get(user.getId()), roles.getOrDefault(user.getId(), Set.of())));
        }
        return summaries;
    }

    public static UserSummary toSummary(AppUser user, PlayerSnapshot player, Set<String> roles) {
        String phoneMasked = maskPhone(user.getPhone());
        return new UserSummary(
                user.getId(),
                player == null ? phoneMasked : player.name(),
                phoneMasked,
                user.getStatus().code(),
                user.getStatus().label(),
                roles,
                player == null ? null : player.type(),
                player == null ? null : PlayerType.of(player.type()).label());
    }

    public static String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }
}
