package com.sora.aitravel.service.impl;

import com.sora.aitravel.model.PoiCandidate;
import org.springframework.stereotype.Component;

/**
 * POI身份标识服务实现
 * 提供POI去重键和名称规范化规则
 */
@Component
public class PoiIdentityServiceImpl {

    /**
     * 生成POI去重键
     * 优先使用源POI ID，其次使用规范化后的名称
     *
     * @param candidate POI候选
     * @return 去重键
     */
    public String dedupKey(PoiCandidate candidate) {
        if (candidate == null) {
            return "";
        }
        if (candidate.getSourcePoiId() != null && !candidate.getSourcePoiId().isBlank()) {
            return candidate.getSourcePoiId();
        }
        return normalizeName(candidate.getName());
    }

    /**
     * 规范化POI名称
     * 移除括号内容、后缀修饰、空格等，统一名称格式
     *
     * @param name 原始名称
     * @return 规范化后的名称
     */
    public String normalizeName(String name) {
        if (name == null) {
            return "";
        }
        return name.replaceAll("[（(].*?[）)]", "")
                .replaceAll("[-—·].*$", "")
                .replace("景区", "")
                .replace("风景区", "")
                .replace("步行街", "")
                .replace("历史文化特色街区", "历史街区")
                .replace("历史文化街区", "历史街区")
                .replace("特色街区", "街区")
                .replaceAll("\\s+", "")
                .trim();
    }
}