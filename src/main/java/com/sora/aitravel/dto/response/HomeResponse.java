package com.sora.aitravel.dto.response;

import java.util.List;

public record HomeResponse(
        List<DestinationResponse> hotDestinations,
        List<NoteListItemResponse> hotNotes,
        List<TagResponse> hotTags) {}
