package com.sora.aitravel.dto.request;

import jakarta.validation.constraints.*;
import java.util.List;

public record CreateNoteRequest(
        @NotBlank String title,
        String coverUrl,
        String destination,
        String summary,
        @NotBlank String content,
        List<Long> tagIds,
        @NotNull @Min(0) @Max(1) Integer status) {}
