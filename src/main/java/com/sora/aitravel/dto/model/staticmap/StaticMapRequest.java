package com.sora.aitravel.dto.model.staticmap;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 静态地图请求参数 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
/** 静态地图请求参数 */
public class StaticMapRequest {

    /** 高德Key 自动填充，无需手动传入 */
    private String key;

    /** 地图中心点 经度,纬度 */
    private String location;

    /** 缩放级别 [1,17] 无标注覆盖物时必填 */
    private Integer zoom;

    /** 图片尺寸 w*h，最大1024*1024，默认400*400 */
    private String size;

    /** 1普通图 2高清图，默认1 */
    private Integer scale;

    /** 标记点集合，多个用|分隔 */
    private String markers;

    /** 文字标签集合，多个用|分隔 */
    private String labels;

    /** 折线/多边形路径，多个用|分隔 */
    private String paths;

    /** 是否展示实时路况 0不展示 1展示，默认0 */
    private Integer traffic;

    public StaticMapRequest(
            String key,
            String location,
            Integer zoom,
            String size,
            Integer scale,
            List<Marker> markers,
            List<Label> labels,
            List<Path> paths,
            Integer traffic) {
        String m = null, l = null, p = null;
        if (markers != null && !markers.isEmpty()) {
            for (Marker marker : markers) {
                if (m == null) {
                    m = marker.toParamString();
                } else {
                    m += "|" + marker.toParamString();
                }
            }
        }

        if (labels != null && !labels.isEmpty()) {
            for (Label label : labels) {
                if (l == null) {
                    l = label.toParamString();
                } else {
                    l += "|" + label.toParamString();
                }
            }
        }

        if (paths != null && !paths.isEmpty()) {
            for (Path path : paths) {
                if (p == null) {
                    p = path.toParamString();
                } else {
                    p += "|" + path.toParamString();
                }
            }
        }

        this.key = key;
        this.location = location;
        this.zoom = zoom;
        this.size = size;
        this.scale = scale;
        this.markers = m;
        this.labels = l;
        this.paths = p;
        this.traffic = traffic;
    }
}
