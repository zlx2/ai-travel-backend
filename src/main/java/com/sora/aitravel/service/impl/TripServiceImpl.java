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

/**
 * 旅行计划 CRUD 服务实现。
 *
 * <p>负责行程的保存、分页查询、详情获取、更新与软删除； 并在获取详情时按需补充附近酒店及住宿区域时间线节点数据。
 */
@Service
@RequiredArgsConstructor
public class TripServiceImpl implements TripService {

    /** 行程来源：AI 生成 */
    private static final int SOURCE_AI = 1;

    /** 行程状态：正常 */
    private static final int STATUS_NORMAL = 1;

    /** 行程状态：已删除（软删除） */
    private static final int STATUS_DELETED = 2;

    /** 删除标记：未删除 */
    private static final int DELETED_NO = 0;

    private final TripMapper tripMapper;
    private final ObjectMapper objectMapper;
    private final NearbyHotelService nearbyHotelService;

    /**
     * 保存 AI 生成的行程计划。
     *
     * <p>将请求中的复杂对象（偏好、需求、行程计划）序列化为 JSON 字符串后持久化， 若未提供标题则自动生成「目的地 + 天数 + 日行程」格式的默认标题。
     *
     * @param request 保存行程请求
     * @return 新建行程的 ID
     */
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

    /**
     * 分页查询当前用户的行程列表。
     *
     * <p>支持按关键词（标题/目的地/摘要模糊匹配）和目的地筛选， 结果按创建时间降序、ID 降序排列。
     *
     * @param pageNum 页码（从 1 开始，null 或小于 1 则默认 1）
     * @param pageSize 每页条数（null 或小于 1 则默认 10，最大 50）
     * @param keyword 搜索关键词（可选）
     * @param destination 目的地筛选（可选）
     * @return 分页结果
     */
    @Override
    public PageResult<TripListItemResponse> listMy(
            Integer pageNum, Integer pageSize, String keyword, String destination) {
        Long userId = StpUtil.getLoginIdAsLong();
        int normalizedPageNum = normalizePageNum(pageNum);
        int normalizedPageSize = normalizePageSize(pageSize);

        LambdaQueryWrapper<Trip> wrapper = new LambdaQueryWrapper<>();
        // 仅查询当前用户未删除的正常状态行程
        wrapper.eq(Trip::getUserId, userId)
                .eq(Trip::getStatus, STATUS_NORMAL)
                .eq(Trip::getDeleted, DELETED_NO);

        // 关键词模糊匹配：标题 OR 目的地 OR 摘要
        if (StringUtils.hasText(keyword)) {
            wrapper.and(
                    w ->
                            w.like(Trip::getTitle, keyword)
                                    .or()
                                    .like(Trip::getDestination, keyword)
                                    .or()
                                    .like(Trip::getSummary, keyword));
        }
        // 目的地精确模糊匹配
        if (StringUtils.hasText(destination)) {
            wrapper.like(Trip::getDestination, destination);
        }
        // 按创建时间降序，相同创建时间按 ID 降序
        wrapper.orderByDesc(Trip::getCreateTime).orderByDesc(Trip::getId);

        Page<Trip> page = new Page<>(normalizedPageNum, normalizedPageSize);
        Page<Trip> result = tripMapper.selectPage(page, wrapper);
        List<TripListItemResponse> list =
                result.getRecords().stream()
                        .map(this::toListItemResponse)
                        .collect(Collectors.toList());

        return new PageResult<>(list, result.getTotal(), normalizedPageNum, normalizedPageSize);
    }

    /**
     * 获取行程详情，并在需要时补充附近酒店数据。
     *
     * <p>若行程计划中某些天缺少附近酒店信息，则调用高德周边搜索进行实时填充， 同时将酒店坐标写入当天的 STAY_AREA 时间线节点。
     *
     * @param id 行程 ID（必须属于当前登录用户）
     * @return 行程详情响应
     */
    @Override
    public TripDetailResponse getDetail(Long id) {
        Trip trip = requireCurrentUserTrip(id);
        TripDetailResponse response = toDetailResponse(trip);
        enrichWithNearbyHotels(response);
        return response;
    }

    /**
     * 按需补充行程计划中缺失的附近酒店数据。
     *
     * <p>将 tripPlanJson 反序列化为 TripPlanDTO，检查每天是否有 nearbyHotels， 若任一天缺失则调用 {@link
     * NearbyHotelService#fillNearbyHotels} 批量补充， 并同步更新每天的 STAY_AREA 时间线节点坐标。失败时静默处理，不影响主流程。
     *
     * @param response 行程详情响应（会被原地修改）
     */
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
            // 检查是否有任一天缺少附近酒店数据
            boolean needsFill =
                    tripPlan.getDailyPlans().stream()
                            .anyMatch(
                                    d ->
                                            d.getNearbyHotels() == null
                                                    || d.getNearbyHotels().isEmpty());
            if (needsFill) {
                // 调用高德周边搜索批量填充
                nearbyHotelService.fillNearbyHotels(tripPlan.getDailyPlans());
                // 将酒店坐标写入每天的 STAY_AREA 时间线节点
                for (TripPlanDTO.DailyPlan day : tripPlan.getDailyPlans()) {
                    enrichStayAreaNode(day);
                }
                response.setTripPlanJson(tripPlan);
            }
        } catch (Exception exception) {
            // 静默失败，不影响主流程
        }
    }

    /**
     * 将附近酒店数据填充到当天的 STAY_AREA 时间线节点。
     *
     * <p>取 hotels 列表中第一家酒店的信息（名称、坐标、地址）写入节点， 并设置 tags 为「酒店 + 距离 + 估算价格」，将节点标记为非紧凑模式（展开显示）。
     *
     * @param dailyPlan 当天行程计划（会被原地修改）
     */
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
                            // 用第一家酒店的基本信息覆盖住宿节点
                            stayNode.setTitle(first.getName());
                            stayNode.setLng(first.getLng());
                            stayNode.setLat(first.getLat());
                            stayNode.setCoordType(first.getCoordType());
                            stayNode.setAddress(first.getAddress());
                            stayNode.setNearbyHotels(hotels);
                            // 展开显示，不紧凑
                            stayNode.setCompact(false);
                            // 标签：酒店 / 距离 / 估算价格
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

    /**
     * 更新行程标题。
     *
     * @param id 行程 ID（必须属于当前登录用户）
     * @param request 更新请求（含新标题）
     */
    @Override
    @Transactional
    public void update(Long id, UpdateTripRequest request) {
        Trip trip = requireCurrentUserTrip(id);
        trip.setTitle(request.getTitle());
        trip.setUpdateTime(LocalDateTime.now());
        tripMapper.updateById(trip);
    }

    /**
     * 软删除行程：先将状态置为已删除并更新，再物理删除记录。
     *
     * <p>注意：当前实现同时执行了 updateById（设置 status=2）和 deleteById（物理删除）， 两者在同一个事务中，实际效果为物理删除。
     *
     * @param id 行程 ID（必须属于当前登录用户）
     */
    @Override
    @Transactional
    public void delete(Long id) {
        Trip trip = requireCurrentUserTrip(id);
        trip.setStatus(STATUS_DELETED);
        trip.setUpdateTime(LocalDateTime.now());
        tripMapper.updateById(trip);
        tripMapper.deleteById(id);
    }

    /**
     * 校验并获取属于当前登录用户的行程记录。
     *
     * <p>同时校验 ID、用户归属、状态正常、未删除四个条件， 不满足时抛出 {@link BusinessException}（NOT_FOUND）。
     *
     * @param id 行程 ID
     * @return 符合条件的行程实体
     * @throws BusinessException 行程不存在或不属于当前用户
     */
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

    /**
     * 将 Trip 实体转换为列表项响应（不含完整行程计划 JSON）。
     *
     * @param trip 行程实体
     * @return 列表项响应
     */
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

    /**
     * 将 Trip 实体转换为详情响应（含完整行程计划 JSON 及需求 JSON）。
     *
     * @param trip 行程实体
     * @return 详情响应
     */
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

    /** 解析行程标题：优先使用请求中的标题，若为空则自动生成「目的地 + 天数 + 日行程」格式。 */
    private String resolveTitle(SaveTripRequest request) {
        if (StringUtils.hasText(request.getTitle())) {
            return request.getTitle();
        }
        return request.getDestination() + " " + request.getDays() + " 日行程";
    }

    /** 规范化页码，null 或小于 1 时默认返回 1。 */
    private int normalizePageNum(Integer pageNum) {
        return pageNum == null || pageNum < 1 ? 1 : pageNum;
    }

    /** 规范化每页条数，null 或小于 1 时默认 10，最大不超过 50。 */
    private int normalizePageSize(Integer pageSize) {
        if (pageSize == null || pageSize < 1) {
            return 10;
        }
        return Math.min(pageSize, 50);
    }

    /**
     * 将对象序列化为 JSON 字符串，null 输入返回 null。
     *
     * @throws BusinessException 序列化失败时抛出
     */
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

    /** 将 JSON 字符串反序列化为 {@code List<String>}，空串或解析失败时返回空列表。 */
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

    /** 将 JSON 字符串反序列化为通用 Object，空串返回 null，解析失败时降级返回原始 JSON 字符串。 */
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
