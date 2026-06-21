package com.sora.aitravel.dto.response;

import java.util.List;

/**
 * 首页数据响应 DTO。
 * <p>聚合首页展示所需的热门目的地、热门游记和热门标签数据。</p>
 *
 * @param hotDestinations 热门目的地列表
 * @param hotNotes        热门游记列表
 * @param hotTags         热门标签列表
 */
public record HomeResponse(
        List<DestinationResponse> hotDestinations,
        List<NoteListItemResponse> hotNotes,
        List<TagResponse> hotTags) {}
