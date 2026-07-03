package com.sora.aitravel.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.sora.aitravel.common.result.*;
import com.sora.aitravel.service.NoteInteractionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 游记互动控制器（点赞/收藏）。
 *
 * <p>接口前缀：/notes/{id}，全局 /api 前缀由 server.servlet.context-path 配置。
 *
 * <p>请求方式：RESTful
 *
 * <p>权限要求：所有接口均需登录（@SaCheckLogin）
 */
@SaCheckLogin
@RestController
@RequestMapping("/notes/{id}")
@RequiredArgsConstructor
public class NoteInteractionController {

    private final NoteInteractionService noteInteractionService;

    /**
     * 点赞指定游记。
     *
     * @param id 游记 ID
     * @return 无返回内容，操作成功即返回成功响应
     */
    @PostMapping("/like")
    public R<Void> like(@PathVariable Long id) {
        noteInteractionService.like(id);
        return R.ok();
    }

    /**
     * 取消点赞指定游记。
     *
     * @param id 游记 ID
     * @return 无返回内容，操作成功即返回成功响应
     */
    @DeleteMapping("/like")
    public R<Void> unlike(@PathVariable Long id) {
        noteInteractionService.unlike(id);
        return R.ok();
    }

    /**
     * 收藏指定游记。
     *
     * @param id 游记 ID
     * @return 无返回内容，操作成功即返回成功响应
     */
    @PostMapping("/favorite")
    public R<Void> favorite(@PathVariable Long id) {
        noteInteractionService.favorite(id);
        return R.ok();
    }

    /**
     * 取消收藏指定游记。
     *
     * @param id 游记 ID
     * @return 无返回内容，操作成功即返回成功响应
     */
    @DeleteMapping("/favorite")
    public R<Void> unfavorite(@PathVariable Long id) {
        noteInteractionService.unfavorite(id);
        return R.ok();
    }
}
