package com.sora.aitravel.controller;

import com.sora.aitravel.common.result.*;
import com.sora.aitravel.dto.response.TagResponse;
import java.util.List;
import org.springframework.web.bind.annotation.*;

/**
 * 标签控制器。
 * <p>接口前缀：/api/tags</p>
 * <p>请求方式：RESTful</p>
 * <p>权限要求：所有接口均为公开，无需登录</p>
 */
@RestController
@RequestMapping("/api/tags")
public class TagController {
    /**
     * 查询标签列表（公开接口）。
     *
     * @param type 标签类型（可选，1-目的地标签，2-游记标签，3-偏好标签），不传则返回全部
     * @return 标签列表（TagResponse）
     */
    @GetMapping
    public R<List<TagResponse>> list(@RequestParam(required = false) Integer type) {
        return ScaffoldResponses.notImplemented();
    }
}
