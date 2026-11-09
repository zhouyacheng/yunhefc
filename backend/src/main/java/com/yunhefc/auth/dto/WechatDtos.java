package com.yunhefc.auth.dto;

import jakarta.validation.constraints.NotBlank;

public final class WechatDtos {

    private WechatDtos() {
    }

    public record AuthorizeResponse(String authorizeUrl) {
    }

    public record CallbackResponse(boolean needBindPhone, String redirect, String csrfToken) {
    }

    public record BindPhoneRequest(
            @NotBlank String phone,
            @NotBlank String code,
            String name,
            Integer position) {
    }
}
