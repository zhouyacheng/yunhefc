package com.yunhefc.auth.dto;

import jakarta.validation.constraints.Size;

public final class WechatDtos {

    private WechatDtos() {
    }

    public record AuthorizeResponse(String authorizeUrl) {
    }

    public record CallbackResponse(boolean needBindPhone, String redirect, String csrfToken) {
    }

    public record BindPhoneRequest(
            @Size(max = 20) String phone,
            @Size(max = 6) String code,
            @Size(max = 32) String name,
            Integer position,
            @Size(max = 64) String inviteToken) {
    }
}
