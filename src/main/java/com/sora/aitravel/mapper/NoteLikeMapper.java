package com.sora.aitravel.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sora.aitravel.entity.NoteLike;
import org.apache.ibatis.annotations.Mapper;

/**
 * 游记点赞 Mapper 接口。
 *
 * <p>对应实体 {@link NoteLike}，操作数据库表 {@code note_like}。 提供游记点赞记录的增删改查能力，支持查询用户是否已点赞、获取某篇游记的点赞列表等。
 */
@Mapper
public interface NoteLikeMapper extends BaseMapper<NoteLike> {}
