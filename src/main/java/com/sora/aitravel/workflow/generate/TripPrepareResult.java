package com.sora.aitravel.workflow.generate;

import com.sora.aitravel.dto.model.TravelRequirementDTO;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class TripPrepareResult {
    private TravelRequirementDTO requirement;
    private List<DaySkeleton> daySkeletons;
    private CityProfile cityProfile;
    private String weatherForecast;
    private String hotelSearchResult;
}
