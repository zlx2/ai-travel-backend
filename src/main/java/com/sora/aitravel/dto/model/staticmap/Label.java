package com.sora.aitravel.dto.model.staticmap;

import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 地图文字标签 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Label {
    /** 【必填】标签展示文字，最大15个字符 */
    private String content;

    /** 字体：0微软雅黑 1宋体 2-Times New Roman 3-Helvetica，默认0 */
    @Builder.Default private Integer font = 0;

    /** 粗体开关：0普通 1粗体，默认0 */
    @Builder.Default private Integer bold = 0;

    /** 字号范围1~72，默认10 */
    @Builder.Default private Integer fontSize = 10;

    /** 文字颜色 0xRRGGBB，默认0xFFFFFF */
    @Builder.Default private String fontColor = "0xFFFFFF";

    /** 标签背景色 0xRRGGBB，默认0x5288d8 */
    @Builder.Default private String background = "0x5288d8";

    /** 【必填】标签绑定坐标集合 */
    @Builder.Default private List<String> locations = new ArrayList<>();

    /** 自动生成labels参数字符串，AI无需手动拼接 */
    public String toParamString() {
        if (locations == null || locations.isEmpty() || content == null) {
            return null;
        }
        return String.format(
                "%s,%d,%d,%d,%s,%s:%s",
                content, font, bold, fontSize, fontColor, background, String.join(";", locations));
    }
}
