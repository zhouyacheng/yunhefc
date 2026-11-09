package com.yunhefc.auth.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yunhefc.auth.domain.AppUser;
import com.yunhefc.auth.dto.AuthDtos.UserView;
import com.yunhefc.auth.infrastructure.UserRoleMapper;
import com.yunhefc.common.Labels;
import com.yunhefc.player.domain.Player;
import com.yunhefc.player.infrastructure.PlayerMapper;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;

@Component
public class UserViewAssembler {

    private final PlayerMapper playerMapper;
    private final UserRoleMapper userRoleMapper;

    public UserViewAssembler(PlayerMapper playerMapper, UserRoleMapper userRoleMapper) {
        this.playerMapper = playerMapper;
        this.userRoleMapper = userRoleMapper;
    }

    public UserView toView(AppUser user) {
        Player player = playerMapper.selectOne(new LambdaQueryWrapper<Player>()
                .eq(Player::getUserId, user.getId())
                .last("LIMIT 1"));
        String phoneMasked = maskPhone(user.getPhone());
        return new UserView(
                user.getId(),
                player == null ? phoneMasked : player.getName(),
                phoneMasked,
                user.getStatus(),
                Labels.userStatus(user.getStatus()),
                new LinkedHashSet<>(userRoleMapper.findRoleCodes(user.getId())),
                user.getCreatedAt());
    }

    public static String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }
}
