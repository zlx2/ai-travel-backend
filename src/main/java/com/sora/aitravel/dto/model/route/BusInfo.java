package com.sora.aitravel.dto.model.route;

import lombok.Data;

import java.util.List;

/**
 * 公交信息
 */
@Data
public class BusInfo {
    private List<BusStep> steps;
}
