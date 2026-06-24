package com.sora.aitravel.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sora.aitravel.entity.NoteComment;
import org.apache.ibatis.annotations.Mapper;

/**
 * 游记评论 Mapper 接口。
 *
 * <p>对应实体 {@link NoteComment}，操作数据库表 {@code note_comment}。 提供游记评论的增删改查能力，支持按游记 ID 查询评论列表、按用户查询其评论等。
 */
@Mapper
public interface NoteCommentMapper extends BaseMapper<NoteComment> {}
