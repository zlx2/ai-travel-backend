package com.sora.aitravel.dto.model.staticmap;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 静态地图响应 注意：静态地图返回的是图片字节数组，而非JSON */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
/** 静态地图响应 */
public class StaticMapResp {

    /** 图片二进制字节数组 */
    private byte[] imageBytes;

    /** 原始请求url */
    private String requestUrl;

    /** 响应content-type */
    private String contentType;

    /** 原始响应文本（异常时存在） */
    private String rawText;
}
