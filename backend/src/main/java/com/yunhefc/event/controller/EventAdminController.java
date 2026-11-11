package com.yunhefc.event.controller;

import com.yunhefc.common.api.ApiResponse;
import com.yunhefc.common.idempotency.Idempotent;
import com.yunhefc.event.application.EventApplicationService;
import com.yunhefc.event.dto.EventDtos.EventCreateRequest;
import com.yunhefc.event.dto.EventDtos.EventDeleteRequest;
import com.yunhefc.event.dto.EventDtos.EventDeleteResponse;
import com.yunhefc.event.dto.EventDtos.EventResponse;
import com.yunhefc.event.dto.EventDtos.EventUpdateRequest;
import com.yunhefc.event.dto.EventDtos.ScoreResponse;
import com.yunhefc.event.dto.EventDtos.ScoreUpdateRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/b")
@RequiredArgsConstructor
public class EventAdminController {

    private final EventApplicationService eventApplicationService;

    @PostMapping("/matches/{matchId}/events")
    @PreAuthorize("hasAuthority('event:edit:any')")
    @Idempotent("EVT-06")
    public ApiResponse<EventResponse> create(@PathVariable Long matchId,
                                             @Valid @RequestBody EventCreateRequest request) {
        return ApiResponse.ok(new EventResponse(eventApplicationService.createAsAdmin(matchId, request)));
    }

    @PatchMapping("/events/{eventId}")
    @PreAuthorize("hasAuthority('event:edit:any')")
    @Idempotent("EVT-07")
    public ApiResponse<EventResponse> update(@PathVariable Long eventId,
                                             @Valid @RequestBody EventUpdateRequest request) {
        return ApiResponse.ok(new EventResponse(eventApplicationService.update(eventId, request)));
    }

    @DeleteMapping("/events/{eventId}")
    @PreAuthorize("hasAuthority('event:delete:any')")
    @Idempotent("EVT-04")
    public ApiResponse<EventDeleteResponse> delete(@PathVariable Long eventId,
                                                   @Valid @RequestBody EventDeleteRequest request) {
        return ApiResponse.ok(eventApplicationService.delete(eventId, request));
    }

    @PatchMapping("/matches/{matchId}/score")
    @PreAuthorize("hasAuthority('event:edit:any')")
    @Idempotent("EVT-05")
    public ApiResponse<ScoreResponse> updateScore(@PathVariable Long matchId,
                                                  @Valid @RequestBody ScoreUpdateRequest request) {
        return ApiResponse.ok(new ScoreResponse(eventApplicationService.updateScore(matchId, request)));
    }
}
