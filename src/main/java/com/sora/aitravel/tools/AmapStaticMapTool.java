package com.sora.aitravel.tools;

import com.sora.aitravel.dto.model.staticmap.StaticMapRequest;
import com.sora.aitravel.dto.model.staticmap.StaticMapResp;
import com.sora.aitravel.service.AmapApiService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AmapStaticMapTool {

    private final AmapApiService amapService;

    /**
     * 工具能力：生成高德静态地图图片，返回图片二进制字节数组，支持中心点、缩放、标记点、文字标签、路线折线/多边形、实时路况 返回结果：StaticMapResp，内部封装地图图片二进制数据
     */
    @Tool(description = "高德静态地图生成工具，入参封装地图配置，返回 StaticMapResp 类，该类的 imageBytes 属性是 静态地图图片的二进制数据")
    public StaticMapResp getStaticMap(
            @ToolParam(
                            description =
                                    """
                    【地图总配置参数 StaticMapRequest】
                    字段清单（AI组装参数专用）：
                    1. location【必填】地图中心点坐标，格式：经度,纬度 例：116.403874,39.914885
                    2. zoom【必填，无覆盖物时强制传】地图缩放等级，取值1~17，默认值10
                    3. size【可选】图片尺寸，格式：宽*高，上限1024*1024，默认400*400
                    4. scale【可选】清晰度：1普通图 / 2高清图，默认1
                    5. markers【可选】标记点集合，多个标记用 | 分隔；由Marker对象列表自动拼接生成，无需手动拼字符串
                    6. labels【可选】文字标签集合，多个标签用 | 分隔；由Label对象列表自动拼接生成，无需手动拼字符串
                    7. paths【可选】路线/多边形集合，多条路径用 | 分隔；由Path对象列表自动拼接生成，无需手动拼字符串
                    8. traffic【可选】是否展示实时路况：0关闭 / 1开启，默认0
                    9. key【自动填充】高德开发者密钥，调用方无需传入，内部自动赋值

                    【子对象组装规则（AI构造参数参考）】
                    ▶ Marker 标记点（支持内置图标/自定义图标二选一，互斥）
                    构造字段：
                    - size：图标大小 small/mid/large，默认small
                    - color：图标颜色 0xRRGGBB，默认0xFC6054
                    - label：图标上单字符标记（数字/大写字母/单个汉字），自定义图标时失效
                    - customIconUrl：自定义图标网络地址，赋值后size/color/label全部失效
                    - locations【必填】坐标集合 List<String>，单个坐标格式经度,纬度
                    拼接格式示例：
                    内置图标：small,0xFC6054,A:116.4,39.9;116.5,39.8
                    自定义图标：-1,https://xxx.png,0:116.4,39.9

                    ▶ Label 文字标签
                    构造字段：
                    - content【必填】标签文字，最多15字符
                    - font：字体 0微软雅黑/1宋体/2Times/3Helvetica，默认0
                    - bold：是否粗体 0否/1是，默认0
                    - fontSize：字号1~72，默认10
                    - fontColor：文字颜色0xRRGGBB，默认0xFFFFFF
                    - background：标签背景色0xRRGGBB，默认0x5288d8
                    - locations【必填】标签坐标集合 List<String>
                    拼接格式示例：门店,0,0,12,0xFFFFFF,0x5288d8:116.4,39.9

                    ▶ Path 路线/多边形
                    构造字段：
                    - weight：线条粗细2~15，默认5
                    - color：线条颜色0xRRGGBB，默认0x0000FF
                    - transparency：线条透明度0~1，默认1.0
                    - fillColor：填充色，传值则闭合为多边形；不传仅折线
                    - fillTransparency：填充透明度0~1，默认0.5，fillColor为空时不生效
                    - locations【必填】路径坐标点集合 List<String>
                    拼接格式示例：5,0x0000FF,1.00,0x00FF00,0.50:116.4,39.9;116.5,39.8

                    【AI调用组装示例】
                    StaticMapRequest request = new StaticMapRequest(
                            null, // key自动填充，传null即可
                            "116.403874,39.914885", // 中心点
                            12, // 缩放
                            "800*800", // 尺寸
                            2, // 高清图
                            List.of(Marker.builder().locations(List.of("116.4,39.9")).build()), // 标记点
                            List.of(Label.builder().content("天安门").locations(List.of("116.4,39.9")).build()), // 文字标签
                            List.of(Path.builder().locations(List.of("116.4,39.9","116.5,39.8")).fillColor("0x00FF00").build()), // 多边形路线
                            1 // 开启路况
                    );
                    """)
                    StaticMapRequest request) {
        return amapService.staticMap(request);
    }

    /**
     * 简化版静态地图生成
     *
     * @param location 坐标
     * @param zoom 缩放等级
     * @param size 图片尺寸
     * @param scale 清晰度
     * @param traffic 是否展示实时路况
     * @return StaticMapResp，其imageBytes属性是图片的二进制数据
     */
    @Tool(description = "简化版静态地图生成，传入基础参数即可生成地图")
    public StaticMapResp getStaticMapSimple(
            @ToolParam(description = "地图中心点坐标，格式：经度,纬度，必填") String location,
            @ToolParam(description = "地图缩放等级，取值1~17，默认10") Integer zoom,
            @ToolParam(description = "图片尺寸，格式：宽*高，默认400*400", required = false) String size,
            @ToolParam(description = "清晰度：1普通图 / 2高清图，默认1", required = false) Integer scale,
            @ToolParam(description = "是否展示实时路况：0关闭 / 1开启，默认0", required = false) Integer traffic) {
        StaticMapRequest request =
                StaticMapRequest.builder()
                        .location(location)
                        .zoom(zoom != null ? zoom : 10)
                        .size(size != null ? size : "400*400")
                        .scale(scale != null ? scale : 1)
                        .traffic(traffic != null ? traffic : 0)
                        .build();
        return amapService.staticMap(request);
    }
}
