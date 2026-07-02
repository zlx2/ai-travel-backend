package com.sora.aitravel.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.sora.aitravel.common.result.*;
import com.sora.aitravel.dto.request.*;
import com.sora.aitravel.dto.response.*;
import com.sora.aitravel.service.NoteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 游记控制器。
 *
 * <p>接口前缀：/notes，全局 /api 前缀由 server.servlet.context-path 配置。
 *
 * <p>请求方式：RESTful
 *
 * <p>权限要求：公开接口（列表查询、查看详情）无需登录；创建、更新、删除需登录（@SaCheckLogin）
 */
@RestController
@RequestMapping("/notes")
@RequiredArgsConstructor
public class NoteController {

    private final NoteService noteService;

    /**
     * 分页查询游记列表（公开接口）。
     *
     * @param pageNum 页码，默认 1
     * @param pageSize 每页条数，默认 10
     * @param keyword 搜索关键字（可选，模糊匹配标题/摘要）
     * @param destination 目的地筛选（可选）
     * @param tagId 标签 ID 筛选（可选）
     * @param sort 排序方式，默认 "latest"（最新），可选 "hot"（最热）
     * @return 分页的游记列表（NoteListItemResponse）
     */
    @GetMapping
    public R<PageResult<NoteListItemResponse>> list(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String destination,
            @RequestParam(required = false) Long tagId,
            @RequestParam(defaultValue = "latest") String sort) {
        return R.ok(noteService.list(pageNum, pageSize, keyword, destination, tagId, sort));
    }

    /**
     * 分页查询当前用户的游记列表（需登录，含草稿和已发布）。
     *
     * @param pageNum 页码，默认 1
     * @param pageSize 每页条数，默认 10
     * @param status 状态筛选（可选，0-草稿，1-已发布），不传返回全部
     * @return 分页的游记列表（NoteListItemResponse）
     */
    @SaCheckLogin
    @GetMapping("/my")
    public R<PageResult<NoteListItemResponse>> listMine(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) Integer status) {
        return R.ok(noteService.listMine(pageNum, pageSize, status));
    }

    /**
     * 创建新的游记（需登录）。
     *
     * @param request 包含标题、封面、目的地、摘要、内容、标签等信息的创建请求
     * @return 创建成功返回新游记的 ID（IdResponse）
     */
    @SaCheckLogin
    @PostMapping
    public R<IdResponse> create(@Valid @RequestBody CreateNoteRequest request) {
        Long id = noteService.create(request);
        return R.ok(new IdResponse(id));
    }

    /**
     * 获取指定游记的详情（公开接口）。
     *
     * @param id 游记 ID
     * @return 游记的详细信息（NoteDetailResponse），包含内容、标签、互动数据等
     */
    @GetMapping("/{id}")
    public R<NoteDetailResponse> detail(@PathVariable Long id) {
        return R.ok(noteService.detail(id));
    }

    /**
     * 更新指定游记（需登录，仅作者可操作）。
     *
     * @param id 游记 ID
     * @param request 包含待更新字段的请求体
     * @return 无返回内容，更新成功即返回成功响应
     */
    @SaCheckLogin
    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @Valid @RequestBody UpdateNoteRequest request) {
        noteService.update(id, request);
        return R.ok();
    }

    /**
     * 删除指定游记（需登录，仅作者可操作）。
     *
     * @param id 游记 ID
     * @return 无返回内容，删除成功即返回成功响应
     */
    @SaCheckLogin
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        noteService.delete(id);
        return R.ok();
    }
}
