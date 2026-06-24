package com.sora.aitravel.dto.model.staticmap;

import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 地图标记点（内置图标 / 自定义图标二选一互斥） */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Marker {
    /** 内置图标大小：small / mid / large，自定义图标时无效，默认small */
    @Builder.Default private String size = "small";

    /** 内置图标颜色 0xRRGGBB，自定义图标时无效，默认0xFC6054 */
    @Builder.Default private String color = "0xFC6054";

    /** 图标上单字符标记（数字/大写字母/单个汉字），自定义图标时无效 */
    private String label;

    /** 自定义图标网络URL，赋值后size、color、label全部失效 */
    private String customIconUrl;

    /** 【必填】标记点坐标集合，单个坐标格式：经度,纬度 */
    @Builder.Default private List<String> locations = new ArrayList<>();

    /** 自动生成接口请求markers分段字符串，AI无需手动拼接 */
    public String toParamString() {
        if (locations == null || locations.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        if (customIconUrl != null && !customIconUrl.isEmpty()) {
            sb.append("-1,").append(customIconUrl).append(",0:");
        } else {
            sb.append(size).append(",").append(color);
            if (label != null && !label.isEmpty()) {
                sb.append(",").append(label);
            }
            sb.append(":");
        }
        sb.append(String.join(";", locations));
        return sb.toString();
    }
}
