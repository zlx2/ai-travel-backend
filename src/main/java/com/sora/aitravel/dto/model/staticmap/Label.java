package com.sora.aitravel.dto.model.staticmap;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 标签样式
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Label {
    /**
     * 标签内容，最多15个字符
     */
    private String content;

    /**
     * 字体：0-微软雅黑，1-宋体，2-Times New Roman，3-Helvetica
     */
    @Builder.Default
    private Integer font = 0;

    /**
     * 粗体：0-非粗体，1-粗体
     */
    @Builder.Default
    private Integer bold = 0;

    /**
     * 字体大小 [1,72]
     */
    @Builder.Default
    private Integer fontSize = 10;

    /**
     * 字体颜色，格式：0xRRGGBB
     */
    @Builder.Default
    private String fontColor = "0xFFFFFF";

    /**
     * 背景色，格式：0xRRGGBB
     */
    @Builder.Default
    private String background = "0x5288d8";

    /**
     * 标签位置列表
     */
    @Builder.Default
    private List<String> locations = new ArrayList<>();

    /**
     * 生成labels参数字符串
     */
    public String toParamString() {
        if (locations == null || locations.isEmpty() || content == null) {
            return null;
        }

        // 格式: content,font,bold,fontSize,fontColor,background:location1;location2
        return String.format("%s,%d,%d,%d,%s,%s:%s",
                content, font, bold, fontSize, fontColor, background,
                String.join(";", locations));
    }
}
