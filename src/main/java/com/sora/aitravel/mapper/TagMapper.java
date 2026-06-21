package com.sora.aitravel.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sora.aitravel.entity.Tag;
import org.apache.ibatis.annotations.Mapper;

/**
 * 标签 Mapper 接口。
 * <p>
 * 对应实体 {@link Tag}，操作数据库表 {@code tag}。
 * 提供标签的增删改查能力，支持按类型（游记标签、偏好标签、目的地标签）查询。
 * </p>
 */
@Mapper
public interface TagMapper extends BaseMapper<Tag> {}
