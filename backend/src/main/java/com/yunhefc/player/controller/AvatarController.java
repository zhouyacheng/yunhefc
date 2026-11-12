package com.yunhefc.player.controller;

import com.yunhefc.common.api.ApiResponse;
import com.yunhefc.player.application.AvatarUploadApplicationService;
import com.yunhefc.player.dto.PlayerDtos.AvatarUploadView;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
public class AvatarController {

    private final AvatarUploadApplicationService avatarService;

    @PostMapping(path = "/api/v1/c/files/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('player:edit:own')")
    public ApiResponse<AvatarUploadView> uploadOwn(@RequestPart("file") MultipartFile file) {
        return ApiResponse.ok(avatarService.uploadOwn(file));
    }

    @PostMapping(path = "/api/v1/b/players/{id}/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('player:edit:any')")
    public ApiResponse<AvatarUploadView> uploadForPlayer(@PathVariable Long id,
                                                         @RequestPart("file") MultipartFile file) {
        return ApiResponse.ok(avatarService.uploadForPlayer(id, file));
    }
}
