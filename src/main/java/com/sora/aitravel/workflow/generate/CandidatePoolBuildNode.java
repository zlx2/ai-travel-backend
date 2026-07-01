package com.sora.aitravel.workflow.generate;

import static com.sora.aitravel.workflow.generate.state.TripGraphStateKeys.CANDIDATE_POOL;
import static com.sora.aitravel.workflow.generate.state.TripGraphStateKeys.CITY_PROFILE;
import static com.sora.aitravel.workflow.generate.state.TripGraphStateKeys.RENTAL_TRIP_CONTEXT;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.sora.aitravel.dto.model.RentalTripContextDTO;
import com.sora.aitravel.workflow.generate.state.TripGraphStateCodec;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** Builds the factual candidate pool AI is allowed to reference. */
@Slf4j
@Component
public class CandidatePoolBuildNode {
    public void execute(GenerateWorkflowContext context) {
        context.setCandidatePool(buildPool(context.getCityProfile(), context.getRentalTripContext()));
    }

    public Map<String, Object> execute(OverAllState state) {
        CandidatePool pool =
                buildPool(
                        TripGraphStateCodec.required(state, CITY_PROFILE, CityProfile.class),
                        TripGraphStateCodec.optional(state, RENTAL_TRIP_CONTEXT, RentalTripContextDTO.class).orElse(null));
        return TripGraphStateCodec.patch(CANDIDATE_POOL, pool);
    }

    private CandidatePool buildPool(CityProfile profile, RentalTripContextDTO rentalTripContext) {
        List<PoiCandidate> scenic = profile == null || profile.getScenicCandidates() == null
                ? List.of()
                : profile.getScenicCandidates();
        LinkedHashMap<String, AreaAnchorCandidate> anchors = new LinkedHashMap<>();
        AreaAnchorCandidate pickup = pickupAnchor(rentalTripContext);
        if (pickup != null) {
            anchors.putIfAbsent(pickup.getId(), pickup);
        }
        addPoiAreas(anchors, profile == null ? null : profile.getFoodCandidates(), "MEAL_AREA");
        addPoiAreas(anchors, profile == null ? null : profile.getHotelCandidates(), "STAY_AREA");
        addScenicClusterAreas(anchors, scenic);
        CandidatePool pool = new CandidatePool(scenic, new ArrayList<>(anchors.values()), pickup);
        log.info(
                "节点[candidate-pool-build]：候选池构建完成，scenic={}, anchors={}",
                pool.getScenicCandidates().size(),
                pool.getAreaAnchors().size());
        return pool;
    }

    private void addPoiAreas(
            LinkedHashMap<String, AreaAnchorCandidate> anchors,
            List<PoiCandidate> candidates,
            String role) {
        if (candidates == null) {
            return;
        }
        for (PoiCandidate candidate : candidates) {
            if (candidate == null || candidate.getLocation() == null || candidate.getLocation().isBlank()) {
                continue;
            }
            String area = firstNonBlank(candidate.getArea(), candidate.getBusinessArea(), candidate.getName());
            String id = stableId(role, firstNonBlank(candidate.getSourcePoiId(), candidate.getName(), area));
            anchors.putIfAbsent(
                    id,
                    new AreaAnchorCandidate(
                            id,
                            area,
                            role,
                            candidate.getCity(),
                            area,
                            candidate.getAddress(),
                            candidate.getLocation(),
                            candidate.getSource(),
                            candidate.getSourcePoiId(),
                            candidate.getBusinessTags()));
        }
    }

    private void addScenicClusterAreas(
            LinkedHashMap<String, AreaAnchorCandidate> anchors, List<PoiCandidate> candidates) {
        if (candidates == null) {
            return;
        }
        for (PoiCandidate candidate : candidates) {
            if (candidate == null || candidate.getLocation() == null || candidate.getLocation().isBlank()) {
                continue;
            }
            String area = firstNonBlank(candidate.getArea(), candidate.getBusinessArea(), candidate.getName());
            if (area == null || area.isBlank()) {
                continue;
            }
            String id =
                    stableId(
                            "SCENIC_CLUSTER",
                            firstNonBlank(candidate.getBusinessArea(), candidate.getArea(), candidate.getSourcePoiId(), candidate.getName()));
            anchors.putIfAbsent(
                    id,
                    new AreaAnchorCandidate(
                            id,
                            area,
                            "SCENIC_CLUSTER",
                            candidate.getCity(),
                            firstNonBlank(candidate.getArea(), candidate.getBusinessArea(), area),
                            candidate.getAddress(),
                            candidate.getLocation(),
                            candidate.getSource(),
                            candidate.getSourcePoiId(),
                            candidate.getBusinessTags()));
        }
    }

    private AreaAnchorCandidate pickupAnchor(RentalTripContextDTO rental) {
        if (rental == null || rental.getMatchedStore() == null) {
            return null;
        }
        if (rental.getMatchedStore().getLng() == null || rental.getMatchedStore().getLat() == null) {
            return null;
        }
        String id = stableId("PICKUP", rental.getMatchedStore().getStoreCode());
        return new AreaAnchorCandidate(
                id,
                firstNonBlank(rental.getMatchedStore().getDisplayName(), "取车区域"),
                "PICKUP",
                rental.getMatchedStore().getCityName(),
                rental.getMatchedStore().getDisplayName(),
                rental.getMatchedStore().getAddress(),
                rental.getMatchedStore().getLng() + "," + rental.getMatchedStore().getLat(),
                rental.getMatchedStore().getSource(),
                rental.getMatchedStore().getAmapPoiId(),
                List.of("取车", "租车"));
    }

    private String stableId(String prefix, String value) {
        return (prefix + "_" + String.valueOf(value)).replaceAll("[^A-Za-z0-9_\\u4e00-\\u9fa5]", "_");
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
