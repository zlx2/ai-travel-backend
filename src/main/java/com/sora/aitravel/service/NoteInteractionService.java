package com.sora.aitravel.service;

public interface NoteInteractionService {
    void like(Long noteId);

    void unlike(Long noteId);

    void favorite(Long noteId);

    void unfavorite(Long noteId);
}
