package com.sora.aitravel.dto.response;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 首页数据响应 DTO。
 *
 * <p>聚合首页展示所需的热门目的地、热门游记和热门标签数据。
 *
 * @param hotDestinations 热门目的地列表
 * @param hotNotes 热门游记列表
 * @param hotTags 热门标签列表
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HomeResponse {

    private List<DestinationResponse> hotDestinations;
    private List<NoteListItemResponse> hotNotes;
    private List<TagResponse> hotTags;
}
