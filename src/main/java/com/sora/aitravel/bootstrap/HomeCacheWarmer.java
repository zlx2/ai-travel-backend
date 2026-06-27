package com.sora.aitravel.bootstrap;

import com.sora.aitravel.service.HomeService;
import java.util.concurrent.CompletableFuture;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class HomeCacheWarmer {

    private final HomeService homeService;

    @EventListener(ApplicationReadyEvent.class)
    public void warmUp() {
        CompletableFuture.runAsync(
                () -> {
                    try {
                        homeService.aggregate();
                        log.info("首页缓存预热完成");
                    } catch (Exception e) {
                        log.warn("首页缓存预热失败，不影响应用启动", e);
                    }
                });
    }
}
