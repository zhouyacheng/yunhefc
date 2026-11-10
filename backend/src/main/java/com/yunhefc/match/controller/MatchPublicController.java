package com.yunhefc.match.controller;

import com.yunhefc.common.api.ApiResponse;
import com.yunhefc.common.api.PageResponse;
import com.yunhefc.match.application.MatchPublicApplicationService;
import com.yunhefc.match.dto.MatchDtos.MatchDetail;
import com.yunhefc.match.dto.MatchDtos.MatchPublicQuery;
import com.yunhefc.match.dto.MatchDtos.MatchSummary;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/c/matches")
@RequiredArgsConstructor
public class MatchPublicController {

    private final MatchPublicApplicationService matchPublicService;

    @GetMapping
    public ApiResponse<PageResponse<MatchSummary>> list(
            @RequestParam(required = false) Integer type,
            @RequestParam(required = false) String venue,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String season,
            @RequestParam(required = false) String month,
            @RequestParam(required = false) String scope,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long pageSize) {
        return ApiResponse.ok(matchPublicService.list(new MatchPublicQuery(
                type, venue, status, season, month, scope, sort, page, pageSize)));
    }

    @GetMapping("/{id}")
    public ApiResponse<MatchDetail> detail(@PathVariable Long id) {
        return ApiResponse.ok(matchPublicService.detail(id));
    }
}
