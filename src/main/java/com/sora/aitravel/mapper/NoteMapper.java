package com.sora.aitravel.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sora.aitravel.entity.Note;
import org.apache.ibatis.annotations.Mapper;

/**
 * 游记 Mapper 接口。
 *
 * <p>对应实体 {@link Note}，操作数据库表 {@code note}。 提供游记的增删改查能力，支持按用户、目的地、状态等条件查询游记列表。
 */
@Mapper
public interface NoteMapper extends BaseMapper<Note> {}
