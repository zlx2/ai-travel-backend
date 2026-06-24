package com.sora.aitravel.test;

import com.sora.aitravel.dto.model.staticmap.StaticMapResp;
import com.sora.aitravel.service.AmapApiService;
import com.sora.aitravel.dto.model.staticmap.Marker;
import com.sora.aitravel.dto.model.staticmap.StaticMapRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.File;
import java.util.List;

/**
 * 静态地图使用示例
 */
@SpringBootTest
public class StaticMapDemo {
    // 初始化服务
    @Autowired
    AmapApiService amapService;

    @Test
    void test() {
        StaticMapRequest request = new StaticMapRequest(null, "115.397128,40.916527", 12, "800*800", 2,
                List.of(new Marker("mid", "0xFF0000", "A", null, List.of("116.397128,39.916527"))),
                null, null, 1);

        try {
            // 1. 请求静态地图
            StaticMapResp resp = amapService.staticMap(request);
            System.out.println("图片字节长度：" + resp.getImageBytes().length);

            // 2. 直接调用service内置方法保存（两种写法任选其一）
            // 写法1：传入字符串路径
            File mapFile = amapService.saveStaticMapImage(resp, "gaode_map.png");
            // 写法2：传入File对象
            // File desktop = new File(System.getProperty("user.home") + "/Desktop/map.png");
            // File mapFile = amapService.saveStaticMapImage(resp, desktop);

            System.out.println("图片保存成功，绝对路径：" + mapFile.getAbsolutePath());
        } catch (IllegalArgumentException e) {
            System.err.println("参数错误：" + e.getMessage());
        } catch (RuntimeException e) {
            System.err.println("操作失败：" + e.getMessage());
            e.printStackTrace();
        }
    }
}
