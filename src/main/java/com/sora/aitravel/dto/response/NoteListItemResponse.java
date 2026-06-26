package com.sora.aitravel.dto.response;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 游记列表项响应 DTO（列表展示用，不含正文内容）。
 *
 * @param id 游记 ID
 * @param title 游记标题
 * @param coverUrl 封面图片 URL
 * @param destination 关联目的地
 * @param summary 游记摘要
 * @param authorId 作者用户 ID
 * @param authorNickname 作者昵称
 * @param authorAvatarUrl 作者头像 URL
 * @param tags 标签名称列表
 * @param likeCount 点赞数
 * @param favoriteCount 收藏数
 * @param commentCount 评论数
 * @param createTime 创建时间
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class NoteListItemResponse {

    private Long id;
    private String title;
    private String coverUrl;
    private String destination;
    private String summary;
    private Long authorId;
    private String authorNickname;
    private String authorAvatarUrl;
    private List<String> tags;
    private Integer likeCount;
    private Integer favoriteCount;
    private Integer commentCount;
    private String createTime;
}
