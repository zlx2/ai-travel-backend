package com.sora.aitravel.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sora.aitravel.common.enums.ErrorCode;
import com.sora.aitravel.common.exception.BusinessException;
import com.sora.aitravel.common.result.PageResult;
import com.sora.aitravel.common.utils.DateTimeUtils;
import com.sora.aitravel.dto.model.TripPlanDTO;
import com.sora.aitravel.dto.request.SaveTripRequest;
import com.sora.aitravel.dto.request.UpdateTripRequest;
import com.sora.aitravel.dto.response.TripDetailResponse;
import com.sora.aitravel.dto.response.TripListItemResponse;
import com.sora.aitravel.entity.Trip;
import com.sora.aitravel.mapper.TripMapper;
import com.sora.aitravel.service.TripService;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** 旅行计划服务实现。 */
@Service
@RequiredArgsConstructor
public class TripServiceImpl implements TripService {

    private static final int SOURCE_AI = 1;
    private static final int STATUS_NORMAL = 1;
    private static final int STATUS_DELETED = 2;
    private static final int DELETED_NO = 0;

    private final TripMapper tripMapper;
    private final ObjectMapper objectMapper;
    private final NearbyHotelService nearbyHotelService;

    @Override
    @Transactional
    public Long save(SaveTripRequest request) {
        Long userId = StpUtil.getLoginIdAsLong();
        LocalDateTime now = LocalDateTime.now();

        Trip trip =
                Trip.builder()
                        .userId(userId)
                        .conversationId(request.getConversationId())
                        .title(resolveTitle(request))
                        .departure(request.getDeparture())
                        .destination(request.getDestination())
                        .days(request.getDays())
                        .budget(request.getBudget())
                        .preferencesJson(toJson(request.getPreferences()))
                        .requirementJson(toJson(request.getRequirementJson()))
                        .tripPlanJson(toJson(request.getTripPlanJson()))
                        .summary(request.getSummary())
                        .coverUrl(request.getCoverUrl())
                        .source(SOURCE_AI)
                        .status(STATUS_NORMAL)
                        .deleted(DELETED_NO)
                        .createTime(now)
                        .updateTime(now)
                        .build();

        tripMapper.insert(trip);
        return trip.getId();
    }

    @Override
    public PageResult<TripListItemResponse> listMy(
            Integer pageNum, Integer pageSize, String keyword, String destination) {
        Long userId = StpUtil.getLoginIdAsLong();
        int normalizedPageNum = normalizePageNum(pageNum);
        int normalizedPageSize = normalizePageSize(pageSize);

        LambdaQueryWrapper<Trip> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Trip::getUserId, userId)
                .eq(Trip::getStatus, STATUS_NORMAL)
                .eq(Trip::getDeleted, DELETED_NO);

        if (StringUtils.hasText(keyword)) {
            wrapper.and(
                    w ->
                            w.like(Trip::getTitle, keyword)
                                    .or()
                                    .like(Trip::getDestination, keyword)
                                    .or()
                                    .like(Trip::getSummary, keyword));
        }
        if (StringUtils.hasText(destination)) {
            wrapper.like(Trip::getDestination, destination);
        }
        wrapper.orderByDesc(Trip::getCreateTime).orderByDesc(Trip::getId);

        Page<Trip> page = new Page<>(normalizedPageNum, normalizedPageSize);
        Page<Trip> result = tripMapper.selectPage(page, wrapper);
        List<TripListItemResponse> list =
                result.getRecords().stream()
                        .map(this::toListItemResponse)
                        .collect(Collectors.toList());

        return new PageResult<>(list, result.getTotal(), normalizedPageNum, normalizedPageSize);
    }

    @Override
    public TripDetailResponse getDetail(Long id) {
        Trip trip = requireCurrentUserTrip(id);
        TripDetailResponse response = toDetailResponse(trip);
        enrichWithNearbyHotels(response);
        return response;
    }

    private void enrichWithNearbyHotels(TripDetailResponse response) {
        try {
            if (response.getTripPlanJson() == null) {
                return;
            }
            TripPlanDTO tripPlan =
                    objectMapper.convertValue(response.getTripPlanJson(), TripPlanDTO.class);
            if (tripPlan.getDailyPlans() == null || tripPlan.getDailyPlans().isEmpty()) {
                return;
            }
            boolean needsFill =
                    tripPlan.getDailyPlans().stream()
                            .anyMatch(
                                    d ->
                                            d.getNearbyHotels() == null
                                                    || d.getNearbyHotels().isEmpty());
            if (needsFill) {
                nearbyHotelService.fillNearbyHotels(tripPlan.getDailyPlans());
                for (TripPlanDTO.DailyPlan day : tripPlan.getDailyPlans()) {
                    enrichStayAreaNode(day);
                }
                response.setTripPlanJson(tripPlan);
            }
        } catch (Exception exception) {
            // 静默失败，不影响主流程
        }
    }

    private void enrichStayAreaNode(TripPlanDTO.DailyPlan dailyPlan) {
        List<TripPlanDTO.NearbyHotel> hotels = dailyPlan.getNearbyHotels();
        if (hotels == null || hotels.isEmpty() || dailyPlan.getTimeline() == null) {
            return;
        }
        dailyPlan.getTimeline().stream()
                .filter(node -> "STAY_AREA".equals(node.getType()))
                .findFirst()
                .ifPresent(
                        stayNode -> {
                            TripPlanDTO.NearbyHotel first = hotels.get(0);
                            stayNode.setTitle(first.getName());
                            stayNode.setLng(first.getLng());
                            stayNode.setLat(first.getLat());
                            stayNode.setCoordType(first.getCoordType());
                            stayNode.setAddress(first.getAddress());
                            stayNode.setNearbyHotels(hotels);
                            stayNode.setCompact(false);
                            stayNode.setTags(
                                    java.util.List.of(
                                            "酒店",
                                            first.getDistanceMeters() != null
                                                    ? first.getDistanceMeters() + "m"
                                                    : "",
                                            first.getEstimatedPrice() != null
                                                    ? first.getEstimatedPrice()
                                                    : ""));
                        });
    }

    @Override
    @Transactional
    public void update(Long id, UpdateTripRequest request) {
        Trip trip = requireCurrentUserTrip(id);
        trip.setTitle(request.getTitle());
        trip.setUpdateTime(LocalDateTime.now());
        tripMapper.updateById(trip);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Trip trip = requireCurrentUserTrip(id);
        trip.setStatus(STATUS_DELETED);
        trip.setUpdateTime(LocalDateTime.now());
        tripMapper.updateById(trip);
        tripMapper.deleteById(id);
    }

    private Trip requireCurrentUserTrip(Long id) {
        Long userId = StpUtil.getLoginIdAsLong();
        Trip trip =
                tripMapper.selectOne(
                        new LambdaQueryWrapper<Trip>()
                                .eq(Trip::getId, id)
                                .eq(Trip::getUserId, userId)
                                .eq(Trip::getStatus, STATUS_NORMAL)
                                .eq(Trip::getDeleted, DELETED_NO)
                                .last("LIMIT 1"));
        if (trip == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "行程不存在");
        }
        return trip;
    }

    private TripListItemResponse toListItemResponse(Trip trip) {
        return new TripListItemResponse(
                trip.getId(),
                trip.getTitle(),
                trip.getDeparture(),
                trip.getDestination(),
                trip.getDays(),
                trip.getBudget(),
                parseList(trip.getPreferencesJson()),
                trip.getSummary(),
                trip.getCoverUrl(),
                DateTimeUtils.format(trip.getCreateTime()));
    }

    private TripDetailResponse toDetailResponse(Trip trip) {
        return new TripDetailResponse(
                trip.getId(),
                trip.getConversationId(),
                trip.getTitle(),
                trip.getDeparture(),
                trip.getDestination(),
                trip.getDays(),
                trip.getBudget(),
                parseList(trip.getPreferencesJson()),
                parseObject(trip.getRequirementJson()),
                parseObject(trip.getTripPlanJson()),
                trip.getSummary(),
                trip.getCoverUrl(),
                trip.getSource(),
                trip.getStatus(),
                DateTimeUtils.format(trip.getCreateTime()),
                DateTimeUtils.format(trip.getUpdateTime()));
    }

    private String resolveTitle(SaveTripRequest request) {
        if (StringUtils.hasText(request.getTitle())) {
            return request.getTitle();
        }
        return request.getDestination() + " " + request.getDays() + " 日行程";
    }

    private int normalizePageNum(Integer pageNum) {
        return pageNum == null || pageNum < 1 ? 1 : pageNum;
    }

    private int normalizePageSize(Integer pageSize) {
        if (pageSize == null || pageSize < 1) {
            return 10;
        }
        return Math.min(pageSize, 50);
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "行程数据保存失败");
        }
    }

    private List<String> parseList(String json) {
        if (!StringUtils.hasText(json)) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private Object parseObject(String json) {
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            return objectMapper.readValue(json, Object.class);
        } catch (Exception e) {
            return json;
        }
    }
}
