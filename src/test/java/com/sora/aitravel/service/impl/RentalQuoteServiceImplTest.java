package com.sora.aitravel.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.sora.aitravel.dto.model.TravelRequirementDTO;
import com.sora.aitravel.mapper.RentalOrderMapper;
import com.sora.aitravel.mapper.RentalPickupPoiMapper;
import com.sora.aitravel.mapper.RentalPriceTemplateMapper;
import com.sora.aitravel.mapper.RentalVehicleGroupMapper;
import com.sora.aitravel.mapper.RentalVehicleModelMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class RentalQuoteServiceImplTest {

    private final RentalQuoteServiceImpl service =
            new RentalQuoteServiceImpl(
                    mock(RentalPickupPoiMapper.class),
                    mock(RentalOrderMapper.class),
                    mock(RentalPriceTemplateMapper.class),
                    mock(RentalVehicleGroupMapper.class),
                    mock(RentalVehicleModelMapper.class),
                    mock(RentalStoreServiceImpl.class));

    @Test
    void normalizeArrivalTextExtractsSearchableAirportName() {
        TravelRequirementDTO requirement = new TravelRequirementDTO();
        requirement.setDestination("成都");

        String normalized =
                ReflectionTestUtils.invokeMethod(
                        service, "normalizeArrivalText", "第一天中午到成都双流机场", requirement);

        assertThat(normalized).isEqualTo("成都双流国际机场");
    }

    @Test
    void normalizeArrivalTextKeepsStationSearchableWithCityPrefix() {
        TravelRequirementDTO requirement = new TravelRequirementDTO();
        requirement.setDestination("杭州");

        String normalized =
                ReflectionTestUtils.invokeMethod(
                        service, "normalizeArrivalText", "周五上午到杭州东站下车", requirement);

        assertThat(normalized).isEqualTo("杭州东站");
    }
}
