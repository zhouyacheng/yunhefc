package com.yunhefc.auth.application;

import com.yunhefc.auth.domain.AppUser;
import com.yunhefc.auth.dto.AuthDtos.UserSummary;
import com.yunhefc.auth.dto.AuthDtos.UserView;
import com.yunhefc.auth.infrastructure.UserRoleMapper;
import com.yunhefc.contracts.player.PlayerReadPort;
import com.yunhefc.contracts.player.PlayerSnapshot;
import com.yunhefc.contracts.player.PlayerType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;

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
                user.getCreatedAt());
    }

    public UserSummary toSummary(AppUser user) {
        PlayerSnapshot player = playerReadPort.findByUserId(user.getId()).orElse(null);
        return new UserSummary(
                user.getId(),
                player == null ? maskPhone(user.getPhone()) : player.name(),
                maskPhone(user.getPhone()),
                user.getStatus().code(),
                user.getStatus().label(),
                new LinkedHashSet<>(userRoleMapper.findRoleCodes(user.getId())),
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
