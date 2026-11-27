package com.yunhefc.content.controller;

import com.yunhefc.common.api.ApiResponse;
import com.yunhefc.common.idempotency.Idempotent;
import com.yunhefc.common.ratelimit.RateLimited;
import com.yunhefc.content.application.ContentReportApplicationService;
import com.yunhefc.content.dto.ContentDtos.ContentReportRequest;
import com.yunhefc.content.dto.ContentDtos.ContentReportResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/c/content-reports")
@RequiredArgsConstructor
public class ContentReportController {

    private final ContentReportApplicationService reportService;

    @PostMapping
    @PreAuthorize("hasAuthority('content:view:any')")
    @Idempotent("CNT-13")
    @RateLimited(key = "content-report", limit = 10, windowSeconds = 60)
    public ApiResponse<ContentReportResult> report(@Valid @RequestBody ContentReportRequest request) {
        return ApiResponse.ok(reportService.report(request));
    }
}
