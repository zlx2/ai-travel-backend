package com.sora.aitravel.dto.model.staticmap;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 折线/多边形样式
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Path {
    /**
     * 线条粗细 [2,15]
     */
    @Builder.Default
    private Integer weight = 5;

    /**
     * 线条颜色，格式：0xRRGGBB
     */
    @Builder.Default
    private String color = "0x0000FF";

    /**
     * 线条透明度 [0,1]
     */
    @Builder.Default
    private Double transparency = 1.0;

    /**
     * 填充颜色（不为空时封闭成多边形）
     */
    private String fillColor;

    /**
     * 填充透明度 [0,1]
     */
    @Builder.Default
    private Double fillTransparency = 0.5;

    /**
     * 路径坐标点列表
     */
    @Builder.Default
    private List<String> locations = new ArrayList<>();

    /**
     * 生成paths参数字符串
     */
    public String toParamString() {
        if (locations == null || locations.isEmpty()) {
            return null;
        }

        // 格式: weight,color,transparency,fillcolor,fillTransparency:location1;location2
        StringBuilder sb = new StringBuilder();
        sb.append(weight).append(",");
        sb.append(color).append(",");
        sb.append(String.format("%.2f", transparency)).append(",");
        sb.append(fillColor != null ? fillColor : "").append(",");
        sb.append(fillColor != null ? String.format("%.2f", fillTransparency) : "");
        sb.append(":");
        sb.append(String.join(";", locations));
        return sb.toString();
    }
}
