package com.yunhefc.activity.controller;

import com.yunhefc.activity.application.ActivityAdminApplicationService;
import com.yunhefc.activity.dto.ActivityDtos.ActivityImageUploadView;
import com.yunhefc.common.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
public class ActivityImageController {

    private final ActivityAdminApplicationService activityAdminService;

    @PostMapping(path = "/api/v1/b/files/activity-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('activity:manage:any')")
    public ApiResponse<ActivityImageUploadView> upload(@RequestPart("file") MultipartFile file) {
        return ApiResponse.ok(activityAdminService.uploadImage(file));
    }
}
