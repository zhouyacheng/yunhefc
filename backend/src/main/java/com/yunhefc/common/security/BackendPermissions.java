package com.yunhefc.common.security;

import java.util.Set;

/**
 * 区分 C 端个人权限与 B 端后台权限。
 * 除自助资料、报名、签到、个人事件、比赛邀请与内容发布外，其余权限均需进入后台使用。
 */
public final class BackendPermissions {

    private static final Set<String> C_ONLY = Set.of(
            "activity:signup:own",
            "attendance:checkin:own",
            "event:create:own",
            "event:edit:own",
            "player:edit:own",
            "registration:create:own",
            "invite:create:match",
            "content:create:own");

    private BackendPermissions() {
    }

    public static boolean hasBackendPermission(Set<String> permissions) {
        return permissions.stream().anyMatch(permission -> !C_ONLY.contains(permission));
    }
}
