package com.sora.aitravel.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.sora.aitravel.common.result.*;
import org.springframework.web.bind.annotation.*;

/**
 * 游记互动控制器（点赞/收藏）。
 * <p>接口前缀：/api/notes/{id}</p>
 * <p>请求方式：RESTful</p>
 * <p>权限要求：所有接口均需登录（@SaCheckLogin）</p>
 */
@SaCheckLogin
@RestController
@RequestMapping("/api/notes/{id}")
public class NoteInteractionController {
    /**
     * 点赞指定游记。
     *
     * @param id 游记 ID
     * @return 无返回内容，操作成功即返回成功响应
     */
    @PostMapping("/like")
    public R<Void> like(@PathVariable Long id) {
        return ScaffoldResponses.notImplemented();
    }

    /**
     * 取消点赞指定游记。
     *
     * @param id 游记 ID
     * @return 无返回内容，操作成功即返回成功响应
     */
    @DeleteMapping("/like")
    public R<Void> unlike(@PathVariable Long id) {
        return ScaffoldResponses.notImplemented();
    }

    /**
     * 收藏指定游记。
     *
     * @param id 游记 ID
     * @return 无返回内容，操作成功即返回成功响应
     */
    @PostMapping("/favorite")
    public R<Void> favorite(@PathVariable Long id) {
        return ScaffoldResponses.notImplemented();
    }

    /**
     * 取消收藏指定游记。
     *
     * @param id 游记 ID
     * @return 无返回内容，操作成功即返回成功响应
     */
    @DeleteMapping("/favorite")
    public R<Void> unfavorite(@PathVariable Long id) {
        return ScaffoldResponses.notImplemented();
    }
}
