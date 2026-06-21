package com.sora.aitravel.service;

import com.sora.aitravel.dto.request.*;
import com.sora.aitravel.dto.response.*;

public interface AiTripService {
    TripAnalyzeResponse analyze(TripAnalyzeRequest request);

    TripGenerateResponse generate(TripGenerateRequest request);
}
