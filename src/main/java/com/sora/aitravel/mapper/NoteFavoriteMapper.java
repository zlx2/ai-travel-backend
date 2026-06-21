package com.sora.aitravel.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sora.aitravel.entity.NoteFavorite;
import org.apache.ibatis.annotations.Mapper;

/**
 * 游记收藏 Mapper 接口。
 * <p>
 * 对应实体 {@link NoteFavorite}，操作数据库表 {@code note_favorite}。
 * 提供游记收藏记录的增删改查能力，支持查询用户是否已收藏、获取用户的收藏列表等。
 * </p>
 */
@Mapper
public interface NoteFavoriteMapper extends BaseMapper<NoteFavorite> {}
