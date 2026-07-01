package com.sora.aitravel.dto.workflow.generate;

import com.sora.aitravel.dto.model.RentalQuoteOptionDTO;
import com.sora.aitravel.dto.model.RentalTripContextDTO;
import com.sora.aitravel.dto.model.TravelRequirementDTO;
import com.sora.aitravel.dto.model.TripPlanDTO;
import com.sora.aitravel.model.trip.generate.CityProfile;
import com.sora.aitravel.model.trip.generate.DaySkeleton;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class DayGenerateInput {
    private Long userId;
    private TravelRequirementDTO requirement;
    private RentalQuoteOptionDTO selectedQuote;
    private RentalTripContextDTO rentalTripContext;
    private List<DaySkeleton> daySkeletons;
    private CityProfile cityProfile;
    private String weatherForecast;
    private String hotelSearchResult;
    private List<TripPlanDTO.DailyPlan> previousDailyPlans;
    private Integer targetDayNo;
    private String revisionText;
}
