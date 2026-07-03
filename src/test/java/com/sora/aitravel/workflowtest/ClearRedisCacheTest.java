package com.sora.aitravel.workflowtest;

import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

/** 一次性清理 Redis 首页脏缓存。跑完可以删掉这个类。 */
@SpringBootTest
class ClearRedisCacheTest {

    @Autowired private StringRedisTemplate redis;

    @Value("${app.cache.key-prefix:plango:dev}")
    private String prefix;

    @Test
    void clearHomeCache() {
        String key = prefix + ":home";
        Boolean deleted = redis.delete(key);
        System.out.println(
                "清理首页缓存 key=" + key + " → " + (Boolean.TRUE.equals(deleted) ? "已删除" : "key不存在"));

        // 顺手列出所有 plango 前缀的 key，看看还有没有其他脏数据
        Set<String> keys = redis.keys(prefix + ":*");
        System.out.println("当前 Redis 中 " + prefix + ":* 的 keys: " + keys);
    }
}
