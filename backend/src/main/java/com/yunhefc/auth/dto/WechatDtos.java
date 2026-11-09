package com.yunhefc.auth.dto;

import com.yunhefc.auth.dto.AuthDtos.PlayerView;
import com.yunhefc.auth.dto.AuthDtos.UserView;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class WechatDtos {

    private WechatDtos() {
    }

    public record AuthorizeResponse(String authorizeUrl) {
    }

    public record CallbackResponse(boolean needBindPhone, String redirect, String csrfToken) {
    }

    public record BindPhoneResponse(UserView user, PlayerView player, String csrfToken) {
    }

    public record BindPhoneRequest(
            @NotBlank String phone,
            @NotBlank String code,
            @Size(max = 32) String name,
            Integer position) {
    }
}
