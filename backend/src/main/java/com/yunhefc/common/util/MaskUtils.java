package com.yunhefc.common.util;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 审计响应脱敏：纯函数，不修改入参。
 */
public final class MaskUtils {

    private static final Set<String> SECRET_KEYS = Set.of("openid", "passwordhash", "tokenhash", "token");
    private static final Pattern IPV6_LITERAL = Pattern.compile("^[0-9A-Fa-f:.]+$");
    private static final String REDACTED = "***";

    private MaskUtils() {
    }

    public static Object mask(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> masked = new LinkedHashMap<>();
            map.forEach((key, item) -> {
                String name = String.valueOf(key);
                masked.put(name, maskField(name, item));
            });
            return masked;
        }
        if (value instanceof Iterable<?> iterable) {
            List<Object> masked = new ArrayList<>();
            iterable.forEach(item -> masked.add(mask(item)));
            return masked;
        }
        if (value instanceof Object[] array) {
            List<Object> masked = new ArrayList<>(array.length);
            for (Object item : array) {
                masked.add(mask(item));
            }
            return masked;
        }
        return value;
    }

    public static String maskPhone(String phone) {
        if (phone == null) {
            return null;
        }
        if (phone.length() < 7) {
            return REDACTED;
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    public static String maskIp(String ip) {
        if (ip == null) {
            return null;
        }
        String value = ip.trim();
        if (value.isEmpty()) {
            return REDACTED;
        }
        if (value.indexOf(':') >= 0) {
            return maskIpv6(value);
        }
        String[] parts = value.split("\\.", -1);
        if (parts.length != 4 || !isIpv4(parts)) {
            return REDACTED;
        }
        return parts[0] + "." + parts[1] + "." + parts[2] + ".*";
    }

    private static Object maskField(String key, Object value) {
        String name = key.toLowerCase(Locale.ROOT);
        if ("phone".equals(name)) {
            return value instanceof String phone ? maskPhone(phone) : (value == null ? null : REDACTED);
        }
        if (SECRET_KEYS.contains(name)) {
            return value == null ? null : REDACTED;
        }
        if ("ip".equals(name)) {
            return value instanceof String ip ? maskIp(ip) : (value == null ? null : REDACTED);
        }
        return mask(value);
    }

    private static String maskIpv6(String value) {
        try {
            if (value.indexOf('%') >= 0) {
                value = value.substring(0, value.indexOf('%'));
            }
            // 仅接受字面量，避免 getByName 对非字面量输入走域名解析。
            if (!IPV6_LITERAL.matcher(value).matches()
                    || !(InetAddress.getByName(value) instanceof Inet6Address address)) {
                return REDACTED;
            }
            String[] groups = address.getHostAddress().split(":");
            if (groups.length < 3) {
                return REDACTED;
            }
            return groups[0] + ":" + groups[1] + ":" + groups[2] + "::*";
        } catch (UnknownHostException ex) {
            return REDACTED;
        }
    }

    private static boolean isIpv4(String[] parts) {
        for (String part : parts) {
            if (part.isEmpty() || part.length() > 3) {
                return false;
            }
            for (int i = 0; i < part.length(); i++) {
                if (!Character.isDigit(part.charAt(i)) || part.charAt(i) > '9') {
                    return false;
                }
            }
            if (Integer.parseInt(part) > 255) {
                return false;
            }
        }
        return true;
    }
}
