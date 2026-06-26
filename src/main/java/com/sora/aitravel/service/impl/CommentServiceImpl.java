package com.sora.aitravel.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sora.aitravel.common.enums.ErrorCode;
import com.sora.aitravel.common.exception.BusinessException;
import com.sora.aitravel.common.result.PageResult;
import com.sora.aitravel.common.utils.LoginUserUtils;
import com.sora.aitravel.dto.request.CreateCommentRequest;
import com.sora.aitravel.dto.response.CommentResponse;
import com.sora.aitravel.entity.Note;
import com.sora.aitravel.entity.NoteComment;
import com.sora.aitravel.entity.SysUser;
import com.sora.aitravel.mapper.NoteCommentMapper;
import com.sora.aitravel.mapper.NoteMapper;
import com.sora.aitravel.mapper.SysUserMapper;
import com.sora.aitravel.service.CommentService;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 评论服务实现类。
 *
 * <p>提供游记评论的 CRUD 功能，包括分页查询评论列表、创建评论和删除评论（仅作者可操作）。 创建评论后同步更新游记的评论计数。
 */
@Service
@RequiredArgsConstructor
public class CommentServiceImpl implements CommentService {

    private final NoteCommentMapper commentMapper;
    private final NoteMapper noteMapper;
    private final SysUserMapper userMapper;

    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public PageResult<CommentResponse> list(Long noteId, Integer pageNum, Integer pageSize) {
        // 确认游记存在
        if (noteMapper.selectById(noteId) == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "游记不存在");
        }

        LambdaQueryWrapper<NoteComment> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(NoteComment::getNoteId, noteId).eq(NoteComment::getStatus, 1);
        wrapper.orderByAsc(NoteComment::getCreateTime);

        Page<NoteComment> page = new Page<>(pageNum, pageSize);
        Page<NoteComment> result = commentMapper.selectPage(page, wrapper);

        List<CommentResponse> list =
                result.getRecords().stream()
                        .map(this::toCommentResponse)
                        .collect(Collectors.toList());

        return new PageResult<>(list, result.getTotal(), pageNum, pageSize);
    }

    @Override
    @Transactional
    public CommentResponse create(Long noteId, CreateCommentRequest request) {
        Long userId = LoginUserUtils.getUserId();

        // 确认游记存在
        Note note = noteMapper.selectById(noteId);
        if (note == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "游记不存在");
        }

        NoteComment comment = new NoteComment();
        comment.setNoteId(noteId);
        comment.setUserId(userId);
        comment.setContent(request.getContent());
        comment.setStatus(1);
        LocalDateTime now = LocalDateTime.now();
        comment.setCreateTime(now);
        comment.setUpdateTime(now);

        commentMapper.insert(comment);

        // 更新游记评论计数
        note.setCommentCount(note.getCommentCount() + 1);
        noteMapper.updateById(note);

        return toCommentResponse(comment);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Long userId = LoginUserUtils.getUserId();
        NoteComment comment = commentMapper.selectById(id);
        if (comment == null || comment.getStatus() != 1) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "评论不存在");
        }
        if (!comment.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "仅评论作者可删除此评论");
        }

        // 软删除：同时设置 status 和 deleted 字段
        comment.setStatus(2);
        comment.setDeleted(1);
        comment.setUpdateTime(LocalDateTime.now());
        commentMapper.updateById(comment);

        // 更新游记评论计数（防止减到负数）
        Note note = noteMapper.selectById(comment.getNoteId());
        if (note != null) {
            note.setCommentCount(Math.max(0, note.getCommentCount() - 1));
            noteMapper.updateById(note);
        }
    }

    private CommentResponse toCommentResponse(NoteComment comment) {
        CommentResponse response = new CommentResponse();
        response.setId(comment.getId());
        response.setNoteId(comment.getNoteId());
        response.setUserId(comment.getUserId());
        response.setContent(comment.getContent());
        response.setCreateTime(
                comment.getCreateTime() != null ? comment.getCreateTime().format(FMT) : null);

        SysUser user = userMapper.selectById(comment.getUserId());
        response.setNickname(user != null ? user.getNickname() : "未知用户");
        response.setAvatarUrl(user != null ? user.getAvatarUrl() : null);

        return response;
    }
}
