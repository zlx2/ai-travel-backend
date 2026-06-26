package com.sora.aitravel.service;

import com.sora.aitravel.common.result.PageResult;
import com.sora.aitravel.dto.request.*;
import com.sora.aitravel.dto.response.*;

/**
 * 游记服务接口。
 *
 * <p>提供游记的完整 CRUD 功能，包括分页列表查询、创建、详情查看、更新和删除。
 */
public interface NoteService {
    /**
     * 分页查询游记列表。
     *
     * @param pageNum 页码（从 1 开始）
     * @param pageSize 每页条数
     * @param keyword 搜索关键词（标题/内容）
     * @param destination 目的地筛选
     * @param tagId 标签筛选
     * @param sort 排序方式，如 "latest"（最新）、"hot"（热门）
     * @return 游记分页结果
     */
    PageResult<NoteListItemResponse> list(
            Integer pageNum,
            Integer pageSize,
            String keyword,
            String destination,
            Long tagId,
            String sort);

    /**
     * 创建游记。
     *
     * @param request 创建游记请求
     * @return 新创建游记的 ID
     */
    Long create(CreateNoteRequest request);

    /**
     * 获取游记详情。
     *
     * @param id 游记 ID
     * @return 游记详细信息
     */
    NoteDetailResponse detail(Long id);

    /**
     * 更新游记。
     *
     * @param id 游记 ID
     * @param request 更新游记请求
     */
    void update(Long id, UpdateNoteRequest request);

    /**
     * 删除游记。
     *
     * @param id 游记 ID
     */
    void delete(Long id);

    /**
     * 分页查询当前用户的游记列表（含草稿）。
     *
     * @param pageNum 页码（从 1 开始）
     * @param pageSize 每页条数
     * @param status 状态筛选（可选，0-草稿，1-已发布），不传返回全部
     * @return 游记分页结果
     */
    PageResult<NoteListItemResponse> listMine(Integer pageNum, Integer pageSize, Integer status);
}
