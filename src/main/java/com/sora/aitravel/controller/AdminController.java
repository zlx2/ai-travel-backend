package com.sora.aitravel.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import com.sora.aitravel.common.result.*;
import com.sora.aitravel.dto.request.DestinationRequest;
import com.sora.aitravel.dto.request.TagRequest;
import com.sora.aitravel.dto.request.UpdateUserStatusRequest;
import com.sora.aitravel.dto.response.DashboardOverviewResponse;
import com.sora.aitravel.dto.response.DestinationResponse;
import com.sora.aitravel.dto.response.TagResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@SaCheckRole("2")
@RestController
@RequestMapping("/api/admin")
public class AdminController {
    @GetMapping("/dashboard/overview")
    public R<DashboardOverviewResponse> dashboard() {
        return ScaffoldResponses.notImplemented();
    }

    @GetMapping("/users")
    public R<PageResult<Object>> users(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String keyword) {
        return ScaffoldResponses.notImplemented();
    }

    @PutMapping("/users/{id}/status")
    public R<Void> updateUserStatus(
            @PathVariable Long id, @Valid @RequestBody UpdateUserStatusRequest request) {
        return ScaffoldResponses.notImplemented();
    }

    @GetMapping("/trips")
    public R<PageResult<Object>> trips(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        return ScaffoldResponses.notImplemented();
    }

    @GetMapping("/trips/{id}")
    public R<Object> trip(@PathVariable Long id) {
        return ScaffoldResponses.notImplemented();
    }

    @DeleteMapping("/trips/{id}")
    public R<Void> deleteTrip(@PathVariable Long id) {
        return ScaffoldResponses.notImplemented();
    }

    @GetMapping("/notes")
    public R<PageResult<Object>> notes(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        return ScaffoldResponses.notImplemented();
    }

    @GetMapping("/notes/{id}")
    public R<Object> note(@PathVariable Long id) {
        return ScaffoldResponses.notImplemented();
    }

    @DeleteMapping("/notes/{id}")
    public R<Void> deleteNote(@PathVariable Long id) {
        return ScaffoldResponses.notImplemented();
    }

    @GetMapping("/comments")
    public R<PageResult<Object>> comments(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        return ScaffoldResponses.notImplemented();
    }

    @DeleteMapping("/comments/{id}")
    public R<Void> deleteComment(@PathVariable Long id) {
        return ScaffoldResponses.notImplemented();
    }

    @GetMapping("/destinations")
    public R<PageResult<DestinationResponse>> destinations(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        return ScaffoldResponses.notImplemented();
    }

    @PostMapping("/destinations")
    public R<IdResponse> createDestination(@Valid @RequestBody DestinationRequest request) {
        return ScaffoldResponses.notImplemented();
    }

    @PutMapping("/destinations/{id}")
    public R<Void> updateDestination(
            @PathVariable Long id, @Valid @RequestBody DestinationRequest request) {
        return ScaffoldResponses.notImplemented();
    }

    @DeleteMapping("/destinations/{id}")
    public R<Void> deleteDestination(@PathVariable Long id) {
        return ScaffoldResponses.notImplemented();
    }

    @GetMapping("/tags")
    public R<PageResult<TagResponse>> tags(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        return ScaffoldResponses.notImplemented();
    }

    @PostMapping("/tags")
    public R<IdResponse> createTag(@Valid @RequestBody TagRequest request) {
        return ScaffoldResponses.notImplemented();
    }

    @PutMapping("/tags/{id}")
    public R<Void> updateTag(@PathVariable Long id, @Valid @RequestBody TagRequest request) {
        return ScaffoldResponses.notImplemented();
    }

    @DeleteMapping("/tags/{id}")
    public R<Void> deleteTag(@PathVariable Long id) {
        return ScaffoldResponses.notImplemented();
    }
}
