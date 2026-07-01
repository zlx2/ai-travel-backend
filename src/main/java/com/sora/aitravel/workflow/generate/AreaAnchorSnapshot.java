package com.sora.aitravel.workflow.generate;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Snapshot of an area anchor stored on generated daily skeletons. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AreaAnchorSnapshot {
    private String id;
    private String name;
    private String role;
    private String city;
    private String area;
    private String address;
    private String location;
}
