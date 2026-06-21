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
 * 前后端联调入口。
 *
 * <p>该接口只回显请求，不访问数据库，也不代表任何业务功能已经实现。
 */
@Slf4j
@RestController
@RequestMapping("/api/debug")
public class ConnectionController {

    @PostMapping("/connect")
    public R<ConnectionCheckResponse> connect(@Valid @RequestBody ConnectionCheckRequest request) {
        log.info("收到前端联调请求，action={}", request.action());
        return R.ok(
                new ConnectionCheckResponse(
                        "后端已收到请求，前后端联通正常", request.action(), LocalDateTime.now()));
    }
}
