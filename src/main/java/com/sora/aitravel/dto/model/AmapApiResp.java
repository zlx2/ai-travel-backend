package com.sora.aitravel.dto.response;

import lombok.Data;

/**
 * 高德API通用响应
 */
@Data
public class AmapApiResponse<T> {
    /**
     * 状态码：1成功，0失败
     */
    private String status;

    /**
     * 状态说明
     */
    private String info;

    /**
     * 返回状态说明，10000 代表正确
     */
    private String infocode;

    /**
     * 结果数量
     */
    private String count;

    /**
     * 具体数据
     */
    private T data;

    /**
     * 原始JSON响应
     */
    private String rawJson;

    public boolean isSuccess() {
        return "1".equals(status);
    }
}
