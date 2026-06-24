package com.sora.aitravel.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 前后端联调检查请求 DTO。
 *
 * <p>前端发起联调检查时携带的最小请求。
 *
 * @param action 联调动作标识（由前端自定义，后端原样回显）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConnectionCheckRequest {

    @NotBlank(message = "联调动作不能为空") private String action;
}
