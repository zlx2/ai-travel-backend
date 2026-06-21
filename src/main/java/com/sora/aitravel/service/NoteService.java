package com.sora.aitravel.service;

import com.sora.aitravel.common.result.PageResult;
import com.sora.aitravel.dto.request.*;
import com.sora.aitravel.dto.response.*;

public interface NoteService {
    PageResult<NoteListItemResponse> list(
            Integer pageNum,
            Integer pageSize,
            String keyword,
            String destination,
            Long tagId,
            String sort);

    Long create(CreateNoteRequest request);

    NoteDetailResponse detail(Long id);

    void update(Long id, UpdateNoteRequest request);

    void delete(Long id);
}
