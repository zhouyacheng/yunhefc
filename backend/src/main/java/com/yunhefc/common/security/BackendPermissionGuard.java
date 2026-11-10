package com.yunhefc.common.security;

import com.yunhefc.contracts.security.AuthPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * 供 @PreAuthorize 使用：判断当前账号是否拥有任一后台权限。
 */
@Component("backendPermissionGuard")
public class BackendPermissionGuard {

    public boolean hasBackendPermission(Authentication authentication) {
        return authentication != null
                && authentication.getPrincipal() instanceof AuthPrincipal principal
                && BackendPermissions.hasBackendPermission(principal.permissions());
    }
}
