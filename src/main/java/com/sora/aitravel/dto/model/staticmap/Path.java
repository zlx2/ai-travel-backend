package com.sora.aitravel.dto.model.staticmap;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 路线折线 / 填充多边形
 * 有fillColor值自动闭合为多边形，无fillColor仅展示折线
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Path {
    /**
     * 线条粗细范围2~15，默认5
     */
    @Builder.Default
    private Integer weight = 5;

    /**
     * 线条颜色 0xRRGGBB，默认0x0000FF
     */
    @Builder.Default
    private String color = "0x0000FF";

    /**
     * 线条透明度 0~1，默认1.0
     */
    @Builder.Default
    private Double transparency = 1.0;

    /**
     * 填充颜色，赋值则图形闭合为多边形；不传仅折线
     */
    private String fillColor;

    /**
     * 填充透明度0~1，仅fillColor存在时生效，默认0.5
     */
    @Builder.Default
    private Double fillTransparency = 0.5;

    /**
     * 【必填】路径连续坐标点集合
     */
    @Builder.Default
    private List<String> locations = new ArrayList<>();

    /**
     * 自动生成paths参数字符串，AI无需手动拼接
     */
    public String toParamString() {
        if (locations == null || locations.isEmpty()) {
            return null;
        }
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
