package com.yunhefc.ledger.controller;

import com.yunhefc.common.api.ApiResponse;
import com.yunhefc.common.api.ItemsResponse;
import com.yunhefc.common.api.PageResponse;
import com.yunhefc.ledger.application.LedgerApplicationService;
import com.yunhefc.ledger.dto.LedgerDtos.LedgerCategoryView;
import com.yunhefc.ledger.dto.LedgerDtos.LedgerEntryQuery;
import com.yunhefc.ledger.dto.LedgerDtos.LedgerEntryView;
import com.yunhefc.ledger.dto.LedgerDtos.LedgerSummaryQuery;
import com.yunhefc.ledger.dto.LedgerDtos.LedgerSummaryView;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/c/ledger")
@RequiredArgsConstructor
public class LedgerController {

    private final LedgerApplicationService ledgerService;

    @GetMapping("/entries")
    @PreAuthorize("hasAuthority('ledger:view:any')")
    public ApiResponse<PageResponse<LedgerEntryView>> entries(
            @RequestParam(required = false) Integer type,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long playerId,
            @RequestParam(required = false) Long activityId,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long pageSize) {
        return ApiResponse.ok(ledgerService.entries(new LedgerEntryQuery(
                type, categoryId, playerId, activityId, from, to, keyword, page, pageSize)));
    }

    @GetMapping("/summary")
    @PreAuthorize("hasAuthority('ledger:view:any')")
    public ApiResponse<LedgerSummaryView> summary(
            @RequestParam(required = false) Integer type,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long playerId,
            @RequestParam(required = false) Long activityId,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        return ApiResponse.ok(ledgerService.summary(
                new LedgerSummaryQuery(type, categoryId, playerId, activityId, from, to)));
    }

    @GetMapping("/categories")
    @PreAuthorize("hasAuthority('ledger:view:any')")
    public ApiResponse<ItemsResponse<LedgerCategoryView>> categories(
            @RequestParam(required = false) Integer type) {
        return ApiResponse.ok(ledgerService.categories(type));
    }
}
