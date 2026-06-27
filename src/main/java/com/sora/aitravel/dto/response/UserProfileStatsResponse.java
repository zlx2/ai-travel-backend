package com.sora.aitravel.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileStatsResponse {
    private Long tripCount;
    private Long noteCount;
    private Long likeCount;
    private Long favoriteCount;
}
