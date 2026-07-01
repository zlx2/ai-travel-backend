package com.sora.aitravel.model.trip.generate;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AreaAnchorCandidate {
    private String id;
    private String name;
    private String role;
    private String city;
    private String area;
    private String address;
    private String location;
    private String source;
    private String sourcePoiId;
    private List<String> tags;
}
