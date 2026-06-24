package com.sora.aitravel.dto.response;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 前后端联调检查响应 DTO。
 *
 * <p>后端收到联调请求后的回显结果。
 *
 * @param message 确认消息
 * @param receivedAction 回显收到的联调动作标识
 * @param receivedAt 服务器收到请求的时间
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConnectionCheckResponse {

    private String message;
    private String receivedAction;
    private LocalDateTime receivedAt;
}
