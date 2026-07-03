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

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 分页查询指定游记的评论列表（按创建时间升序）。
     *
     * @param noteId 游记 ID
     * @param pageNum 页码（从 1 开始）
     * @param pageSize 每页条数
     * @return 分页评论列表（含用户昵称、头像）
     */
    @Override
    public PageResult<CommentResponse> list(Long noteId, Integer pageNum, Integer pageSize) {
        // 确认游记存在，不存在则抛异常
        if (noteMapper.selectById(noteId) == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "游记不存在");
        }

        // 构建查询条件：指定游记 + 状态为正常（status=1）
        LambdaQueryWrapper<NoteComment> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(NoteComment::getNoteId, noteId).eq(NoteComment::getStatus, 1);
        // 按创建时间升序排列（最早的评论排在前面）
        wrapper.orderByAsc(NoteComment::getCreateTime);

        // 执行分页查询
        Page<NoteComment> page = new Page<>(pageNum, pageSize);
        Page<NoteComment> result = commentMapper.selectPage(page, wrapper);

        // 将 NoteComment 实体转换为 CommentResponse（含用户昵称、头像）
        List<CommentResponse> list =
                result.getRecords().stream()
                        .map(this::toCommentResponse)
                        .collect(Collectors.toList());

        return new PageResult<>(list, result.getTotal(), pageNum, pageSize);
    }

    /**
     * 创建评论，并同步更新游记的评论计数。
     *
     * @param noteId 游记 ID
     * @param request 评论内容（包含 content 字段）
     * @return 新创建的评论响应（含用户昵称、头像）
     */
    @Override
    @Transactional
    public CommentResponse create(Long noteId, CreateCommentRequest request) {
        // 获取当前登录用户 ID
        Long userId = LoginUserUtils.getUserId();

        // 校验游记存在性
        Note note = noteMapper.selectById(noteId);
        if (note == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "游记不存在");
        }

        // 当前时间戳
        LocalDateTime now = LocalDateTime.now();
        // 构建评论实体
        NoteComment comment =
                NoteComment.builder()
                        .noteId(noteId) // 关联游记 ID
                        .userId(userId) // 评论作者 ID
                        .content(request.getContent()) // 评论内容
                        .status(1) // 状态：1=正常
                        .createTime(now)
                        .updateTime(now)
                        .build();

        // 插入评论到数据库
        commentMapper.insert(comment);

        // 同步更新游记的评论计数 +1
        note.setCommentCount(note.getCommentCount() + 1);
        noteMapper.updateById(note);

        // 返回新评论的完整信息
        return toCommentResponse(comment);
    }

    /**
     * 删除评论（软删除，仅评论作者可操作）。同时更新游记评论计数。
     *
     * @param id 评论 ID
     */
    @Override
    @Transactional
    public void delete(Long id) {
        // 获取当前登录用户 ID
        Long userId = LoginUserUtils.getUserId();
        // 查询评论记录
        NoteComment comment = commentMapper.selectById(id);
        // 校验评论存在且状态为正常
        if (comment == null || comment.getStatus() != 1) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "评论不存在");
        }
        // 权限校验：仅评论作者可删除自己的评论
        if (!comment.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "仅评论作者可删除此评论");
        }

        // 软删除：设置状态为 2（已删除）+ 逻辑删除标记为 1
        comment.setStatus(2);
        comment.setDeleted(1);
        comment.setUpdateTime(LocalDateTime.now());
        commentMapper.updateById(comment);

        // 更新游记评论计数（使用 Math.max 防止减到负数）
        Note note = noteMapper.selectById(comment.getNoteId());
        if (note != null) {
            note.setCommentCount(Math.max(0, note.getCommentCount() - 1));
            noteMapper.updateById(note);
        }
    }

    /**
     * 将 NoteComment 实体转换为 CommentResponse（含评论者昵称、头像）。
     *
     * @param comment 评论实体
     * @return CommentResponse 评论响应 DTO
     */
    private CommentResponse toCommentResponse(NoteComment comment) {
        // 查询评论者信息
        SysUser user = userMapper.selectById(comment.getUserId());
        return CommentResponse.builder()
                .id(comment.getId())
                .noteId(comment.getNoteId())
                .userId(comment.getUserId())
                .content(comment.getContent())
                .createTime(
                        comment.getCreateTime() != null
                                ? comment.getCreateTime().format(FMT)
                                : null)
                .nickname(user != null ? user.getNickname() : "未知用户") // 评论者昵称
                .avatarUrl(user != null ? user.getAvatarUrl() : null) // 评论者头像
                .build();
    }
}
