package com.sora.aitravel.workflow.generate;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CandidatePool {
    private List<PoiCandidate> scenicCandidates;
    private List<AreaAnchorCandidate> areaAnchors;
    private AreaAnchorCandidate pickupAnchor;
}
