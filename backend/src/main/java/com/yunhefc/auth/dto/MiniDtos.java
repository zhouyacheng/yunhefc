package com.yunhefc.auth.dto;

import com.yunhefc.auth.dto.AuthDtos.PlayerView;
import com.yunhefc.auth.dto.AuthDtos.UserView;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Set;

public final class MiniDtos {

    private MiniDtos() {
    }

    public record MiniLoginRequest(@NotBlank @Size(max = 128) String code) {
    }

    public record MiniLoginResponse(
            boolean needBindPhone,
            String bindTicket,
            UserView user,
            PlayerView player,
            Set<String> permissions,
            String csrfToken) {
    }

    public record MiniBindPhoneRequest(
            @NotBlank @Size(max = 128) String bindTicket,
            @NotBlank @Size(max = 20) String phone,
            @NotBlank @Size(max = 6) String code,
            @Size(max = 32) String name,
            Integer position) {
    }
}
