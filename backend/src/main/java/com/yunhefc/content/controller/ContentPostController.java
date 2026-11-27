package com.yunhefc.content.controller;

import com.yunhefc.common.api.ApiResponse;
import com.yunhefc.common.api.PageResponse;
import com.yunhefc.common.idempotency.Idempotent;
import com.yunhefc.content.application.ContentPostApplicationService;
import com.yunhefc.content.dto.ContentDtos.PostCreateRequest;
import com.yunhefc.content.dto.ContentDtos.PostDeleteResult;
import com.yunhefc.content.dto.ContentDtos.PostLikeRequest;
import com.yunhefc.content.dto.ContentDtos.PostLikeResult;
import com.yunhefc.content.dto.ContentDtos.PostView;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/c/posts")
@RequiredArgsConstructor
public class ContentPostController {

    private final ContentPostApplicationService postService;

    @GetMapping
    @PreAuthorize("hasAuthority('content:view:any')")
    public ApiResponse<PageResponse<PostView>> list(
            @RequestParam(required = false) Long albumId,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long pageSize) {
        return ApiResponse.ok(postService.list(albumId, page, pageSize));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('content:create:own')")
    @Idempotent("CNT-10")
    public ApiResponse<PostView> create(@Valid @RequestBody PostCreateRequest request) {
        return ApiResponse.ok(postService.create(request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('content:create:own','content:delete:any')")
    @Idempotent("CNT-11")
    public ApiResponse<PostDeleteResult> delete(@PathVariable Long id) {
        return ApiResponse.ok(postService.delete(id));
    }

    @PutMapping("/{id}/like")
    @PreAuthorize("hasAuthority('content:create:own')")
    public ApiResponse<PostLikeResult> setLike(@PathVariable Long id,
                                               @Valid @RequestBody PostLikeRequest request) {
        return ApiResponse.ok(postService.setLike(id, request.liked()));
    }
}
