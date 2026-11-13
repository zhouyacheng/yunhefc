package com.yunhefc.auth.controller;

import com.yunhefc.auth.application.WechatAuthService;
import com.yunhefc.auth.dto.AuthDtos.SessionResponse;
import com.yunhefc.auth.dto.WechatDtos.AuthorizeResponse;
import com.yunhefc.auth.dto.WechatDtos.BindPhoneRequest;
import com.yunhefc.auth.dto.WechatDtos.CallbackResponse;
import com.yunhefc.common.api.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.Locale;

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

    /**
     * 微信浏览器按 HTML 导航回跳：按结果 302 到站内页面；其余请求保持 JSON 契约。
     */
    @GetMapping("/callback")
    public ResponseEntity<?> callback(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state,
            @RequestHeader(value = HttpHeaders.ACCEPT, required = false) String accept,
            HttpServletRequest request,
            HttpServletResponse response) {
        if (isHtmlNavigation(accept)) {
            String location = wechatAuthService.callbackLanding(code, state, request, response);
            return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(location)).build();
        }
        return ResponseEntity.ok(ApiResponse.ok(wechatAuthService.callback(code, state, request, response)));
    }

    @PostMapping("/bind-phone")
    public ApiResponse<SessionResponse> bindPhone(@Valid @RequestBody BindPhoneRequest request,
                                                    HttpServletRequest servletRequest,
                                                    HttpServletResponse response) {
        return ApiResponse.ok(wechatAuthService.bindPhone(request, servletRequest, response));
    }

    private static boolean isHtmlNavigation(String accept) {
        if (accept == null) {
            return false;
        }
        String value = accept.toLowerCase(Locale.ROOT);
        return value.contains("text/html") && !value.contains("application/json");
    }
}
