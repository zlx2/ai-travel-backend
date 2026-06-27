package com.sora.aitravel.dto.model.route;

import java.util.List;
import lombok.Data;

/** 公交信息 */
@Data
public class BusInfo {
    private List<BusLine> buslines;
}
