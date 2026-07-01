package com.sora.aitravel.dto.workflow.generate;

import com.sora.aitravel.dto.model.TravelRequirementDTO;
import com.sora.aitravel.model.trip.generate.CityProfile;
import com.sora.aitravel.model.trip.generate.DaySkeleton;
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
