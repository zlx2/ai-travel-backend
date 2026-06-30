package com.sora.aitravel.workflow.generate;

import org.springframework.stereotype.Component;

/** Shared POI identity and name normalization rules. */
@Component
public class PoiIdentityService {
    public String dedupKey(PoiCandidate candidate) {
        if (candidate == null) {
            return "";
        }
        if (candidate.getSourcePoiId() != null && !candidate.getSourcePoiId().isBlank()) {
            return candidate.getSourcePoiId();
        }
        return normalizeName(candidate.getName());
    }

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
