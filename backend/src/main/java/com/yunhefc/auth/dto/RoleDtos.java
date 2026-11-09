package com.yunhefc.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public final class RoleDtos {

    private RoleDtos() {
    }

    public record RoleView(Long id, String code, String name, boolean isDefault, List<String> permissions) {
    }

    public record PermissionView(Long id, String code, String name, String resource, String action, String scope) {
    }

    public record RoleCreateRequest(@NotBlank String code, @NotBlank String name, List<String> permissions) {
    }

    public record RoleRenameRequest(@NotBlank String name) {
    }

    public record RolePermissionsRequest(@NotEmpty List<String> permissions) {
    }

    public record UserRolesRequest(@NotEmpty List<Long> roles) {
    }

    public record UserRolesView(Long userId, List<String> roles) {
    }
}
