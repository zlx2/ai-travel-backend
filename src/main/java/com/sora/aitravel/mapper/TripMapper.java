package com.sora.aitravel.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sora.aitravel.entity.Trip;
import org.apache.ibatis.annotations.Mapper;

/**
 * 行程 Mapper 接口。
 * <p>
 * 对应实体 {@link Trip}，操作数据库表 {@code trip}。
 * 提供旅行行程的增删改查能力，支持按用户查看行程列表、按条件筛选等。
 * </p>
 */
@Mapper
public interface TripMapper extends BaseMapper<Trip> {}
