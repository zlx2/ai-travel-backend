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

    @Override
    @Transactional
    public void like(Long noteId) {
        Long userId = LoginUserUtils.getUserId();
        Note note = requirePublishedNote(noteId);

        // 幂等：已点赞则直接返回
        boolean exists =
                noteLikeMapper.selectCount(
                                new LambdaQueryWrapper<NoteLike>()
                                        .eq(NoteLike::getNoteId, noteId)
                                        .eq(NoteLike::getUserId, userId))
                        > 0;
        if (exists) return;

        NoteLike like = NoteLike.builder().noteId(noteId).userId(userId).build();
        noteLikeMapper.insert(like);

        note.setLikeCount(note.getLikeCount() + 1);
        noteMapper.updateById(note);
    }

    @Override
    @Transactional
    public void unlike(Long noteId) {
        Long userId = LoginUserUtils.getUserId();
        Note note = requirePublishedNote(noteId);

        boolean removed =
                noteLikeMapper.delete(
                                new LambdaQueryWrapper<NoteLike>()
                                        .eq(NoteLike::getNoteId, noteId)
                                        .eq(NoteLike::getUserId, userId))
                        > 0;
        if (!removed) return;

        note.setLikeCount(Math.max(0, note.getLikeCount() - 1));
        noteMapper.updateById(note);
    }

    @Override
    @Transactional
    public void favorite(Long noteId) {
        Long userId = LoginUserUtils.getUserId();
        Note note = requirePublishedNote(noteId);

        boolean exists =
                noteFavoriteMapper.selectCount(
                                new LambdaQueryWrapper<NoteFavorite>()
                                        .eq(NoteFavorite::getNoteId, noteId)
                                        .eq(NoteFavorite::getUserId, userId))
                        > 0;
        if (exists) return;

        NoteFavorite favorite = NoteFavorite.builder().noteId(noteId).userId(userId).build();
        noteFavoriteMapper.insert(favorite);

        note.setFavoriteCount(note.getFavoriteCount() + 1);
        noteMapper.updateById(note);
    }

    @Override
    @Transactional
    public void unfavorite(Long noteId) {
        Long userId = LoginUserUtils.getUserId();
        Note note = requirePublishedNote(noteId);

        boolean removed =
                noteFavoriteMapper.delete(
                                new LambdaQueryWrapper<NoteFavorite>()
                                        .eq(NoteFavorite::getNoteId, noteId)
                                        .eq(NoteFavorite::getUserId, userId))
                        > 0;
        if (!removed) return;

        note.setFavoriteCount(Math.max(0, note.getFavoriteCount() - 1));
        noteMapper.updateById(note);
    }

    private Note requirePublishedNote(Long noteId) {
        Note note = noteMapper.selectById(noteId);
        if (note == null || note.getStatus() != NoteStatusEnum.PUBLISHED.ordinal()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "游记不存在");
        }
        return note;
    }
}
