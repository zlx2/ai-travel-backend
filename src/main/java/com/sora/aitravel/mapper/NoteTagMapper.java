package com.sora.aitravel.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sora.aitravel.entity.NoteTag;
import org.apache.ibatis.annotations.Mapper;

/**
 * 游记-标签关联 Mapper 接口。
 *
 * <p>对应实体 {@link NoteTag}，操作数据库表 {@code note_tag}。 提供游记与标签的多对多关联关系查询和维护能力。
 */
@Mapper
public interface NoteTagMapper extends BaseMapper<NoteTag> {}
