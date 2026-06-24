package com.sora.aitravel.dto.request;

import com.sora.aitravel.dto.model.TravelRequirementDTO;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** AI 旅行需求分析请求。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TripAnalyzeRequest {

    private String conversationId;
    private String userInput;
    private TravelRequirementDTO formInput;
    private List<String> extraAnswers;
    private TravelRequirementDTO requirement;
    private String selectedDestination;
}
