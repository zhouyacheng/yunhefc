package com.yunhefc.auth.controller;

import com.yunhefc.auth.application.AuthService;
import com.yunhefc.auth.application.RoleService;
import com.yunhefc.auth.dto.AuthDtos.PhoneUpdateRequest;
import com.yunhefc.auth.dto.AuthDtos.UserSummary;
import com.yunhefc.auth.dto.AuthDtos.UserView;
import com.yunhefc.auth.dto.RoleDtos.PermissionView;
import com.yunhefc.auth.dto.RoleDtos.RoleCreateRequest;
import com.yunhefc.auth.dto.RoleDtos.RolePermissionsRequest;
import com.yunhefc.auth.dto.RoleDtos.RoleRenameRequest;
import com.yunhefc.auth.dto.RoleDtos.RoleView;
import com.yunhefc.auth.dto.RoleDtos.UserRolesRequest;
import com.yunhefc.auth.dto.RoleDtos.UserRolesView;
import com.yunhefc.common.api.ApiResponse;
import com.yunhefc.common.api.ItemsResponse;
import com.yunhefc.common.api.PageQuery;
import com.yunhefc.common.api.PageResponse;
import com.yunhefc.common.idempotency.Idempotent;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/b")
@RequiredArgsConstructor
public class RoleController {

    private final RoleService roleService;
    private final AuthService authService;

    @GetMapping("/roles")
    @PreAuthorize("hasAuthority('role:view:any')")
    public ApiResponse<ItemsResponse<RoleView>> roles() {
        return ApiResponse.ok(new ItemsResponse<>(roleService.listRoles()));
    }

    @PostMapping("/roles")
    @PreAuthorize("hasAuthority('role:manage:any')")
    public ApiResponse<RoleView> create(@Valid @RequestBody RoleCreateRequest request) {
        return ApiResponse.ok(roleService.create(request));
    }

    @PatchMapping("/roles/{id}")
    @PreAuthorize("hasAuthority('role:manage:any')")
    public ApiResponse<RoleView> rename(@PathVariable Long id, @Valid @RequestBody RoleRenameRequest request) {
        return ApiResponse.ok(roleService.rename(id, request.name()));
    }

    @DeleteMapping("/roles/{id}")
    @PreAuthorize("hasAuthority('role:manage:any')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        roleService.delete(id);
        return ApiResponse.ok();
    }

    @PutMapping("/roles/{id}/permissions")
    @PreAuthorize("hasAuthority('role:manage:any')")
    @Idempotent("ROL-05")
    public ApiResponse<RoleView> permissions(@PathVariable Long id,
                                             @Valid @RequestBody RolePermissionsRequest request) {
        return ApiResponse.ok(roleService.setPermissions(id, request.permissions()));
    }

    @GetMapping("/permissions")
    @PreAuthorize("hasAuthority('role:view:any')")
    public ApiResponse<ItemsResponse<PermissionView>> permissions() {
        return ApiResponse.ok(new ItemsResponse<>(roleService.listPermissions()));
    }

    @PutMapping("/users/{id}/roles")
    @PreAuthorize("hasAuthority('role:manage:any')")
    @Idempotent("ROL-07")
    public ApiResponse<UserRolesView> assignRoles(@PathVariable Long id,
                                                  @Valid @RequestBody UserRolesRequest request) {
        return ApiResponse.ok(roleService.assignRoles(id, request));
    }

    @GetMapping("/users")
    @PreAuthorize("hasAuthority('role:manage:any')")
    public ApiResponse<PageResponse<UserSummary>> users(
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long pageSize) {
        PageQuery query = PageQuery.of(page, pageSize);
        return ApiResponse.ok(authService.listUsers(status, query.page(), query.pageSize()));
    }

    @PatchMapping("/users/{id}/phone")
    @PreAuthorize("hasAuthority('player:edit:any')")
    public ApiResponse<UserView> changePhone(@PathVariable Long id, @Valid @RequestBody PhoneUpdateRequest request) {
        return ApiResponse.ok(authService.changePhone(id, request.phone()));
    }

    @DeleteMapping("/users/{id}/wechat-binding")
    @PreAuthorize("hasAuthority('player:edit:any')")
    public ApiResponse<Void> unbindWechat(@PathVariable Long id) {
        authService.unbindWechat(id);
        return ApiResponse.ok();
    }
}
