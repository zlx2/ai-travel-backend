package com.sora.aitravel.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** AI 行程生成 SSE 进度事件。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TripGenerateProgressEvent {
    private String type;
    private String node;
    private String label;
    private Integer progress;
    private TripGenerateResponse data;
    private String message;
}
