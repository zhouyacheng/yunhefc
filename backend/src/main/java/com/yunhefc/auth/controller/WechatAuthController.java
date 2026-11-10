package com.yunhefc.auth.controller;

import com.yunhefc.auth.application.WechatAuthService;
import com.yunhefc.auth.dto.WechatDtos.AuthorizeResponse;
import com.yunhefc.auth.dto.WechatDtos.BindPhoneRequest;
import com.yunhefc.auth.dto.WechatDtos.BindPhoneResponse;
import com.yunhefc.auth.dto.WechatDtos.CallbackResponse;
import com.yunhefc.common.api.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/c/auth/wechat")
@RequiredArgsConstructor
public class WechatAuthController {

    private final WechatAuthService wechatAuthService;

    @GetMapping("/authorize")
    public ApiResponse<AuthorizeResponse> authorize(
            @RequestParam(defaultValue = "/") String redirect,
            @RequestHeader(value = "User-Agent", defaultValue = "") String userAgent,
            HttpServletRequest request) {
        return ApiResponse.ok(wechatAuthService.authorize(redirect, userAgent, request));
    }

    @GetMapping("/callback")
    public ApiResponse<CallbackResponse> callback(
            @RequestParam String code,
            @RequestParam String state,
            HttpServletRequest request,
            HttpServletResponse response) {
        return ApiResponse.ok(wechatAuthService.callback(code, state, request, response));
    }

    @PostMapping("/bind-phone")
    public ApiResponse<BindPhoneResponse> bindPhone(@Valid @RequestBody BindPhoneRequest request,
                                                    HttpServletRequest servletRequest,
                                                    HttpServletResponse response) {
        return ApiResponse.ok(wechatAuthService.bindPhone(request, servletRequest, response));
    }
}
