package com.sora.aitravel.dto.response;

public record DashboardOverviewResponse(
        Long userCount, Long tripCount, Long noteCount, Long commentCount) {}
