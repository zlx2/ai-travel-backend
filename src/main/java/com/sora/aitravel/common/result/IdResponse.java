package com.sora.aitravel.common.result;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 通用 ID 响应。
 *
 * <p>用于创建资源后仅需返回 ID 的场景，如注册、创建游记等。 使用 Lombok 生成构造方法、getter、setter、equals、hashCode 和 toString。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class IdResponse {

    private Long id;
}
