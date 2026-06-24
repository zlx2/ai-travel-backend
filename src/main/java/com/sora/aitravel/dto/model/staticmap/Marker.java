package com.sora.aitravel.dto.model.staticmap;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 标注样式
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Marker {
    /**
     * 标注大小：small, mid, large
     */
    @Builder.Default
    private String size = "small";

    /**
     * 标注颜色，格式：0xRRGGBB
     */
    @Builder.Default
    private String color = "0xFC6054";

    /**
     * 标注文字：[0-9]、[A-Z]、单个中文字
     */
    private String label;

    /**
     * 自定义图片URL（使用此参数时，size/color/label无效）
     */
    private String customIconUrl;

    /**
     * 标注位置列表
     */
    @Builder.Default
    private List<String> locations = new ArrayList<>();

    /**
     * 生成markers参数字符串
     */
    public String toParamString() {
        if (locations == null || locations.isEmpty()) {
            return null;
        }

        StringBuilder sb = new StringBuilder();

        if (customIconUrl != null && !customIconUrl.isEmpty()) {
            // 自定义图片格式: -1,url,0:location1;location2
            sb.append("-1,").append(customIconUrl).append(",0:");
        } else {
            // 系统样式格式: size,color,label:location1;location2
            sb.append(size).append(",");
            sb.append(color);
            if (label != null && !label.isEmpty()) {
                sb.append(",").append(label);
            }
            sb.append(":");
        }

        sb.append(String.join(";", locations));
        return sb.toString();
    }
}
