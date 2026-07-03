package com.sora.aitravel.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sora.aitravel.common.enums.ErrorCode;
import com.sora.aitravel.common.enums.NoteStatusEnum;
import com.sora.aitravel.common.exception.BusinessException;
import com.sora.aitravel.common.utils.LoginUserUtils;
import com.sora.aitravel.entity.Note;
import com.sora.aitravel.entity.NoteFavorite;
import com.sora.aitravel.entity.NoteLike;
import com.sora.aitravel.mapper.NoteFavoriteMapper;
import com.sora.aitravel.mapper.NoteLikeMapper;
import com.sora.aitravel.mapper.NoteMapper;
import com.sora.aitravel.service.NoteInteractionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 游记互动服务实现类。
 *
 * <p>提供用户对游记的点赞和收藏功能。点赞/收藏为幂等操作： 重复点赞不会产生重复记录，取消不存在的点赞也不会报错。 每次操作后同步更新游记的对应计数。
 */
@Service
@RequiredArgsConstructor
public class NoteInteractionServiceImpl implements NoteInteractionService {

    private final NoteMapper noteMapper;
    private final NoteLikeMapper noteLikeMapper;
    private final NoteFavoriteMapper noteFavoriteMapper;

    /**
     * 对游记点赞（幂等操作）。重复点赞不会产生重复记录。
     *
     * @param noteId 游记 ID
     */
    @Override
    @Transactional
    public void like(Long noteId) {
        // 获取当前登录用户 ID
        Long userId = LoginUserUtils.getUserId();
        // 校验游记存在且为已发布状态
        Note note = requirePublishedNote(noteId);

        // 幂等检查：查询 note_like 表是否已存在该用户对该游记的点赞记录
        boolean exists =
                noteLikeMapper.selectCount(
                                new LambdaQueryWrapper<NoteLike>()
                                        .eq(NoteLike::getNoteId, noteId)
                                        .eq(NoteLike::getUserId, userId))
                        > 0;
        // 已点赞则直接返回，不重复插入
        if (exists) return;

        // 构建点赞记录并插入数据库
        NoteLike like = NoteLike.builder().noteId(noteId).userId(userId).build();
        noteLikeMapper.insert(like);

        // 同步更新游记的点赞计数 +1
        note.setLikeCount(note.getLikeCount() + 1);
        noteMapper.updateById(note);
    }

    /**
     * 取消对游记的点赞。取消不存在的点赞不会报错。
     *
     * @param noteId 游记 ID
     */
    @Override
    @Transactional
    public void unlike(Long noteId) {
        // 获取当前登录用户 ID
        Long userId = LoginUserUtils.getUserId();
        // 校验游记存在且为已发布状态
        Note note = requirePublishedNote(noteId);

        // 删除 note_like 表中该用户对该游记的点赞记录，返回受影响行数
        boolean removed =
                noteLikeMapper.delete(
                                new LambdaQueryWrapper<NoteLike>()
                                        .eq(NoteLike::getNoteId, noteId)
                                        .eq(NoteLike::getUserId, userId))
                        > 0;
        // 若未删除任何记录（用户未点赞），直接返回
        if (!removed) return;

        // 同步更新游记的点赞计数 -1，使用 Math.max 防止减到负数
        note.setLikeCount(Math.max(0, note.getLikeCount() - 1));
        noteMapper.updateById(note);
    }

    /**
     * 收藏游记（幂等操作）。重复收藏不会产生重复记录。
     *
     * @param noteId 游记 ID
     */
    @Override
    @Transactional
    public void favorite(Long noteId) {
        // 获取当前登录用户 ID
        Long userId = LoginUserUtils.getUserId();
        // 校验游记存在且为已发布状态
        Note note = requirePublishedNote(noteId);

        // 幂等检查：查询 note_favorite 表是否已存在该用户对该游记的收藏记录
        boolean exists =
                noteFavoriteMapper.selectCount(
                                new LambdaQueryWrapper<NoteFavorite>()
                                        .eq(NoteFavorite::getNoteId, noteId)
                                        .eq(NoteFavorite::getUserId, userId))
                        > 0;
        // 已收藏则直接返回
        if (exists) return;

        // 构建收藏记录并插入数据库
        NoteFavorite favorite = NoteFavorite.builder().noteId(noteId).userId(userId).build();
        noteFavoriteMapper.insert(favorite);

        // 同步更新游记的收藏计数 +1
        note.setFavoriteCount(note.getFavoriteCount() + 1);
        noteMapper.updateById(note);
    }

    /**
     * 取消收藏游记。取消不存在的收藏不会报错。
     *
     * @param noteId 游记 ID
     */
    @Override
    @Transactional
    public void unfavorite(Long noteId) {
        // 获取当前登录用户 ID
        Long userId = LoginUserUtils.getUserId();
        // 校验游记存在且为已发布状态
        Note note = requirePublishedNote(noteId);

        // 删除 note_favorite 表中该用户对该游记的收藏记录
        boolean removed =
                noteFavoriteMapper.delete(
                                new LambdaQueryWrapper<NoteFavorite>()
                                        .eq(NoteFavorite::getNoteId, noteId)
                                        .eq(NoteFavorite::getUserId, userId))
                        > 0;
        // 未收藏则直接返回
        if (!removed) return;

        // 同步更新游记的收藏计数 -1，防止减到负数
        note.setFavoriteCount(Math.max(0, note.getFavoriteCount() - 1));
        noteMapper.updateById(note);
    }

    /**
     * 校验游记是否存在且为已发布状态。未发布或不存在时抛出 NOT_FOUND 异常。
     *
     * @param noteId 游记 ID
     * @return 已发布的 Note 实体
     */
    private Note requirePublishedNote(Long noteId) {
        Note note = noteMapper.selectById(noteId);
        // 不存在或非已发布状态 → 抛出"游记不存在"异常
        if (note == null || note.getStatus() != NoteStatusEnum.PUBLISHED.ordinal()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "游记不存在");
        }
        return note;
    }
}
