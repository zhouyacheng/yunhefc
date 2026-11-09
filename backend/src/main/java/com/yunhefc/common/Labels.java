package com.yunhefc.common;

public final class Labels {

    private Labels() {
    }

    public static String userStatus(int status) {
        return switch (status) {
            case 0 -> "PENDING";
            case 1 -> "ACTIVE";
            case 2 -> "DISABLED";
            default -> "UNKNOWN";
        };
    }

    public static String playerType(int type) {
        return type == 1 ? "PLAYER" : "GUEST";
    }

    public static String position(int position) {
        return switch (position) {
            case 0 -> "GOALKEEPER";
            case 1 -> "DEFENDER";
            case 2 -> "MIDFIELDER";
            case 3 -> "FORWARD";
            default -> "UNKNOWN";
        };
    }

    public static String inviteType(int type) {
        return type == 1 ? "MATCH" : "TEAM";
    }

    public static String inviteStatus(int status) {
        return switch (status) {
            case 0 -> "ACTIVE";
            case 1 -> "REVOKED";
            case 2 -> "EXPIRED";
            default -> "UNKNOWN";
        };
    }
}
