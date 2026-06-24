package com.sora.aitravel.controller;

import com.sora.aitravel.common.result.R;
import com.sora.aitravel.dto.request.ConnectionCheckRequest;
import com.sora.aitravel.dto.response.ConnectionCheckResponse;
import jakarta.validation.Valid;
import java.time.LocalDateTime;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 前后端联调控制器。
 *
 * <p>接口前缀：/api/debug
 *
 * <p>请求方式：POST
 *
 * <p>权限要求：公开接口，无需登录
 *
 * <p>该接口只回显请求，不访问数据库，也不代表任何业务功能已经实现。
 */
@Slf4j
@RestController
@RequestMapping("/api/debug")
public class ConnectionController {

    /**
     * 前后端联通性检查。
     *
     * <p>接收前端发送的联调请求，回显收到的 action 内容及当前时间，用于验证前后端网络联通正常。
     *
     * @param request 包含联调动作标识的请求体
     * @return 回显结果，包含确认消息、收到的 action 和服务器时间（ConnectionCheckResponse）
     */
    @PostMapping("/connect")
    public R<ConnectionCheckResponse> connect(@Valid @RequestBody ConnectionCheckRequest request) {
        log.info("收到前端联调请求，action={}", request.getAction());
        return R.ok(
                new ConnectionCheckResponse(
                        "后端已收到请求，前后端联通正常", request.getAction(), LocalDateTime.now()));
    }
}
