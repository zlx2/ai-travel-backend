package com.sora.aitravel.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sora.aitravel.entity.Destination;
import org.apache.ibatis.annotations.Mapper;

/**
 * 目的地 Mapper 接口。
 *
 * <p>对应实体 {@link Destination}，操作数据库表 {@code destination}。 提供目的地的增删改查能力，支持按名称模糊搜索、按热度排序、按省市筛选等。
 */
@Mapper
public interface DestinationMapper extends BaseMapper<Destination> {}
