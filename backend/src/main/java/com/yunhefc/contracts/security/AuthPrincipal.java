package com.yunhefc.contracts.security;

import java.io.Serializable;
import java.util.Set;

public record AuthPrincipal(
        Long userId,
        String phone,
        int status,
        Set<String> roles,
        Set<String> permissions) implements Serializable {
}
