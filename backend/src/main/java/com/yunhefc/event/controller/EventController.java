package com.yunhefc.event.controller;

import com.yunhefc.common.api.ApiResponse;
import com.yunhefc.common.idempotency.Idempotent;
import com.yunhefc.event.application.EventApplicationService;
import com.yunhefc.event.dto.EventDtos.EventCreateRequest;
import com.yunhefc.event.dto.EventDtos.EventResponse;
import com.yunhefc.event.dto.EventDtos.EventTimeline;
import com.yunhefc.event.dto.EventDtos.EventUpdateRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/c")
@RequiredArgsConstructor
public class EventController {

    private final EventApplicationService eventApplicationService;

    @GetMapping("/matches/{matchId}/events")
    public ApiResponse<EventTimeline> timeline(@PathVariable Long matchId) {
        return ApiResponse.ok(eventApplicationService.timeline(matchId));
    }

    @PostMapping("/matches/{matchId}/events")
    @PreAuthorize("hasAnyAuthority('event:create:own', 'event:edit:any')")
    @Idempotent("EVT-02")
    public ApiResponse<EventResponse> create(@PathVariable Long matchId,
                                             @Valid @RequestBody EventCreateRequest request) {
        return ApiResponse.ok(new EventResponse(eventApplicationService.create(matchId, request)));
    }

    @PatchMapping("/events/{eventId}")
    @PreAuthorize("hasAnyAuthority('event:edit:own', 'event:edit:any')")
    @Idempotent("EVT-03")
    public ApiResponse<EventResponse> update(@PathVariable Long eventId,
                                             @Valid @RequestBody EventUpdateRequest request) {
        return ApiResponse.ok(new EventResponse(eventApplicationService.update(eventId, request)));
    }
}
