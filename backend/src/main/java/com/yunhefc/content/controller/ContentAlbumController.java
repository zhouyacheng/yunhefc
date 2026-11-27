package com.yunhefc.content.controller;

import com.yunhefc.common.api.ApiResponse;
import com.yunhefc.common.api.PageResponse;
import com.yunhefc.common.idempotency.Idempotent;
import com.yunhefc.content.application.ContentAlbumApplicationService;
import com.yunhefc.content.dto.ContentDtos.AlbumCreateRequest;
import com.yunhefc.content.dto.ContentDtos.AlbumDeleteResult;
import com.yunhefc.content.dto.ContentDtos.AlbumDetail;
import com.yunhefc.content.dto.ContentDtos.AlbumPhotoDeleteResult;
import com.yunhefc.content.dto.ContentDtos.AlbumPhotoUploadView;
import com.yunhefc.content.dto.ContentDtos.AlbumPhotoView;
import com.yunhefc.content.dto.ContentDtos.AlbumSummary;
import com.yunhefc.content.dto.ContentDtos.AlbumUpdateRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/c/albums")
@RequiredArgsConstructor
public class ContentAlbumController {

    private final ContentAlbumApplicationService albumService;

    @GetMapping
    @PreAuthorize("hasAuthority('content:view:any')")
    public ApiResponse<PageResponse<AlbumSummary>> list(
            @RequestParam(required = false) Long matchId,
            @RequestParam(required = false) Long activityId,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long pageSize) {
        return ApiResponse.ok(albumService.list(matchId, activityId, page, pageSize));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('content:view:any')")
    public ApiResponse<AlbumDetail> detail(@PathVariable Long id) {
        return ApiResponse.ok(albumService.detail(id));
    }

    @GetMapping("/{id}/photos")
    @PreAuthorize("hasAuthority('content:view:any')")
    public ApiResponse<PageResponse<AlbumPhotoView>> photos(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "30") long pageSize) {
        return ApiResponse.ok(albumService.photos(id, page, pageSize));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('content:create:own')")
    @Idempotent("CNT-04")
    public ApiResponse<AlbumDetail> create(@Valid @RequestBody AlbumCreateRequest request) {
        return ApiResponse.ok(albumService.create(request));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('content:manage:any')")
    @Idempotent("CNT-05")
    public ApiResponse<AlbumDetail> update(@PathVariable Long id,
                                           @Valid @RequestBody AlbumUpdateRequest request) {
        return ApiResponse.ok(albumService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('content:manage:any')")
    @Idempotent("CNT-06")
    public ApiResponse<AlbumDeleteResult> delete(@PathVariable Long id) {
        return ApiResponse.ok(albumService.delete(id));
    }

    @PostMapping(path = "/{id}/photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('content:create:own')")
    public ApiResponse<AlbumPhotoUploadView> uploadPhoto(@PathVariable Long id,
                                                         @RequestPart("file") MultipartFile file) {
        return ApiResponse.ok(albumService.uploadPhoto(id, file));
    }

    @DeleteMapping("/{id}/photos/{photoId}")
    @PreAuthorize("hasAnyAuthority('content:create:own','content:delete:any')")
    @Idempotent("CNT-08")
    public ApiResponse<AlbumPhotoDeleteResult> deletePhoto(@PathVariable Long id,
                                                           @PathVariable Long photoId) {
        return ApiResponse.ok(albumService.deletePhoto(id, photoId));
    }
}
