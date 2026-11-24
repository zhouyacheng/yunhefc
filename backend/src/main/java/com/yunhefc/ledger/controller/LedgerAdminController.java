package com.yunhefc.ledger.controller;

import com.yunhefc.common.api.ApiResponse;
import com.yunhefc.common.api.ItemsResponse;
import com.yunhefc.common.api.PageResponse;
import com.yunhefc.common.idempotency.Idempotent;
import com.yunhefc.ledger.application.LedgerAdminApplicationService;
import com.yunhefc.ledger.dto.LedgerDtos.LedgerCategoryCreateRequest;
import com.yunhefc.ledger.dto.LedgerDtos.LedgerCategoryUpdateRequest;
import com.yunhefc.ledger.dto.LedgerDtos.LedgerCategoryView;
import com.yunhefc.ledger.dto.LedgerDtos.LedgerDeleteRequest;
import com.yunhefc.ledger.dto.LedgerDtos.LedgerDeletedResult;
import com.yunhefc.ledger.dto.LedgerDtos.LedgerEntryCreateRequest;
import com.yunhefc.ledger.dto.LedgerDtos.LedgerEntryQuery;
import com.yunhefc.ledger.dto.LedgerDtos.LedgerEntryUpdateRequest;
import com.yunhefc.ledger.dto.LedgerDtos.LedgerEntryView;
import com.yunhefc.ledger.dto.LedgerDtos.LedgerSummaryQuery;
import com.yunhefc.ledger.dto.LedgerDtos.LedgerSummaryView;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/b/ledger")
@RequiredArgsConstructor
public class LedgerAdminController {

    private final LedgerAdminApplicationService ledgerAdminService;

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
        return ApiResponse.ok(ledgerAdminService.entries(new LedgerEntryQuery(
                type, categoryId, playerId, activityId, from, to, keyword, page, pageSize)));
    }

    @GetMapping("/summary")
    @PreAuthorize("hasAuthority('ledger:view:any')")
    public ApiResponse<LedgerSummaryView> summary(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        return ApiResponse.ok(ledgerAdminService.summary(
                new LedgerSummaryQuery(from, to, null, null)));
    }

    @PostMapping("/entries")
    @PreAuthorize("hasAuthority('ledger:manage:any')")
    @Idempotent("LED-04")
    public ApiResponse<LedgerEntryView> create(@Valid @RequestBody LedgerEntryCreateRequest request) {
        return ApiResponse.ok(ledgerAdminService.create(request));
    }

    @PatchMapping("/entries/{id}")
    @PreAuthorize("hasAuthority('ledger:manage:any')")
    @Idempotent("LED-05")
    public ApiResponse<LedgerEntryView> update(@PathVariable Long id,
                                               @Valid @RequestBody LedgerEntryUpdateRequest request) {
        return ApiResponse.ok(ledgerAdminService.update(id, request));
    }

    @DeleteMapping("/entries/{id}")
    @PreAuthorize("hasAuthority('ledger:manage:any')")
    @Idempotent("LED-06")
    public ApiResponse<LedgerDeletedResult> delete(@PathVariable Long id,
                                                   @Valid @RequestBody LedgerDeleteRequest request) {
        return ApiResponse.ok(ledgerAdminService.delete(id, request));
    }

    @GetMapping("/categories")
    @PreAuthorize("hasAuthority('ledger:manage:any')")
    public ApiResponse<ItemsResponse<LedgerCategoryView>> categories(
            @RequestParam(required = false) Integer type,
            @RequestParam(required = false) Boolean includeInactive) {
        return ApiResponse.ok(ledgerAdminService.categories(type, includeInactive));
    }

    @PostMapping("/categories")
    @PreAuthorize("hasAuthority('ledger:manage:any')")
    @Idempotent("LED-10")
    public ApiResponse<LedgerCategoryView> createCategory(
            @Valid @RequestBody LedgerCategoryCreateRequest request) {
        return ApiResponse.ok(ledgerAdminService.createCategory(request));
    }

    @PatchMapping("/categories/{id}")
    @PreAuthorize("hasAuthority('ledger:manage:any')")
    @Idempotent("LED-11")
    public ApiResponse<LedgerCategoryView> updateCategory(
            @PathVariable Long id,
            @Valid @RequestBody LedgerCategoryUpdateRequest request) {
        return ApiResponse.ok(ledgerAdminService.updateCategory(id, request));
    }
}
