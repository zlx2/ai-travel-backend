package com.sora.aitravel.dto.response;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 游记详情响应 DTO。
 *
 * @param id 游记 ID
 * @param title 游记标题
 * @param coverUrl 封面图片 URL
 * @param destination 关联目的地
 * @param summary 游记摘要
 * @param content 游记正文内容
 * @param authorId 作者用户 ID
 * @param authorNickname 作者昵称
 * @param authorAvatarUrl 作者头像 URL
 * @param tags 关联标签列表
 * @param viewCount 浏览数
 * @param likeCount 点赞数
 * @param favoriteCount 收藏数
 * @param commentCount 评论数
 * @param liked 当前登录用户是否已点赞
 * @param favorited 当前登录用户是否已收藏
 * @param status 发布状态（0-草稿，1-已发布）
 * @param createTime 创建时间
 * @param updateTime 更新时间
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class NoteDetailResponse {

    private Long id;
    private String title;
    private String coverUrl;
    private String destination;
    private String summary;
    private String content;
    private Long authorId;
    private String authorNickname;
    private String authorAvatarUrl;
    private List<TagResponse> tags;
    private Integer viewCount;
    private Integer likeCount;
    private Integer favoriteCount;
    private Integer commentCount;
    private Boolean liked;
    private Boolean favorited;
    private Integer status;
    private String createTime;
    private String updateTime;
}
