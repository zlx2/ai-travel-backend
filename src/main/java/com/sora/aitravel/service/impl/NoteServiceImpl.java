package com.sora.aitravel.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sora.aitravel.common.enums.ErrorCode;
import com.sora.aitravel.common.enums.NoteStatusEnum;
import com.sora.aitravel.common.exception.BusinessException;
import com.sora.aitravel.common.result.PageResult;
import com.sora.aitravel.common.utils.LoginUserUtils;
import com.sora.aitravel.dto.request.CreateNoteRequest;
import com.sora.aitravel.dto.request.UpdateNoteRequest;
import com.sora.aitravel.dto.response.NoteDetailResponse;
import com.sora.aitravel.dto.response.NoteListItemResponse;
import com.sora.aitravel.entity.Note;
import com.sora.aitravel.entity.NoteFavorite;
import com.sora.aitravel.entity.NoteLike;
import com.sora.aitravel.entity.NoteTag;
import com.sora.aitravel.entity.SysUser;
import com.sora.aitravel.entity.Tag;
import com.sora.aitravel.mapper.NoteFavoriteMapper;
import com.sora.aitravel.mapper.NoteLikeMapper;
import com.sora.aitravel.mapper.NoteMapper;
import com.sora.aitravel.mapper.NoteTagMapper;
import com.sora.aitravel.mapper.SysUserMapper;
import com.sora.aitravel.mapper.TagMapper;
import com.sora.aitravel.service.NoteService;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 游记服务实现类。
 *
 * <p>提供游记的完整 CRUD 功能，包括分页列表查询（支持关键字/目的地/标签筛选和排序）、 创建游记（含标签关联）、查看详情（含作者信息和当前用户互动状态）、更新游记
 * （仅作者可操作）和删除游记（软删除，仅作者可操作）。
 */
@Service
@RequiredArgsConstructor
public class NoteServiceImpl implements NoteService {

    private final NoteMapper noteMapper;
    private final NoteTagMapper noteTagMapper;
    private final NoteLikeMapper noteLikeMapper;
    private final NoteFavoriteMapper noteFavoriteMapper;
    private final TagMapper tagMapper;
    private final SysUserMapper userMapper;
    private final StringRedisTemplate stringRedisTemplate;

    @Value("${app.cache.key-prefix:plango:dev}")
    private String cacheKeyPrefix;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public PageResult<NoteListItemResponse> list(
            Integer pageNum,
            Integer pageSize,
            String keyword,
            String destination,
            Long tagId,
            String sort) {

        LambdaQueryWrapper<Note> wrapper = new LambdaQueryWrapper<>();
        // 仅展示已发布的游记
        wrapper.eq(Note::getStatus, NoteStatusEnum.PUBLISHED.ordinal());

        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w.like(Note::getTitle, keyword).or().like(Note::getSummary, keyword));
        }
        if (destination != null && !destination.isBlank()) {
            wrapper.eq(Note::getDestination, destination);
        }
        if (tagId != null && tagId > 0) {
            // 通过子查询筛选包含指定标签的游记
            List<Long> noteIds = getNoteIdsByTagId(tagId);
            if (noteIds.isEmpty()) {
                return new PageResult<>(Collections.emptyList(), 0L, pageNum, pageSize);
            }
            wrapper.in(Note::getId, noteIds);
        }
        if ("hot".equals(sort)) {
            wrapper.orderByDesc(Note::getLikeCount);
        } else {
            wrapper.orderByDesc(Note::getCreateTime);
        }

        Page<Note> page = new Page<>(pageNum, pageSize);
        Page<Note> result = noteMapper.selectPage(page, wrapper);

        List<NoteListItemResponse> list =
                result.getRecords().stream()
                        .map(this::toListItemResponse)
                        .collect(Collectors.toList());

        return new PageResult<>(list, result.getTotal(), pageNum, pageSize);
    }

    @Override
    public PageResult<NoteListItemResponse> listMine(
            Integer pageNum, Integer pageSize, Integer status) {
        Long userId = LoginUserUtils.getUserId();

        LambdaQueryWrapper<Note> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Note::getUserId, userId);
        // 排除已删除的游记
        wrapper.ne(Note::getStatus, NoteStatusEnum.DELETED.ordinal());
        if (status != null) {
            wrapper.eq(Note::getStatus, status);
        }
        wrapper.orderByDesc(Note::getUpdateTime);

        Page<Note> page = new Page<>(pageNum, pageSize);
        Page<Note> result = noteMapper.selectPage(page, wrapper);

        List<NoteListItemResponse> list =
                result.getRecords().stream()
                        .map(this::toListItemResponse)
                        .collect(Collectors.toList());

        return new PageResult<>(list, result.getTotal(), pageNum, pageSize);
    }

    @Override
    @Transactional
    public Long create(CreateNoteRequest request) {
        Long userId = LoginUserUtils.getUserId();
        LocalDateTime now = LocalDateTime.now();

        Note note =
                Note.builder()
                        .userId(userId)
                        .title(request.getTitle())
                        .coverUrl(request.getCoverUrl())
                        .destination(request.getDestination())
                        .summary(request.getSummary())
                        .content(request.getContent())
                        .status(request.getStatus())
                        .viewCount(0)
                        .likeCount(0)
                        .favoriteCount(0)
                        .commentCount(0)
                        .createTime(now)
                        .updateTime(now)
                        .build();

        noteMapper.insert(note);
        clearHomeCache();

        // 关联标签
        if (request.getTagIds() != null && !request.getTagIds().isEmpty()) {
            saveNoteTags(note.getId(), request.getTagIds());
        }

        return note.getId();
    }

    @Override
    public NoteDetailResponse detail(Long id) {
        Note note = requireNote(id);

        // 草稿仅作者本人可查看
        if (note.getStatus() == NoteStatusEnum.DRAFT.ordinal()) {
            try {
                Long userId = LoginUserUtils.getUserId();
                if (!note.getUserId().equals(userId)) {
                    throw new BusinessException(ErrorCode.NOT_FOUND, "游记不存在");
                }
            } catch (BusinessException e) {
                throw e;
            } catch (Exception e) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "游记不存在");
            }
        }

        SysUser author = userMapper.selectById(note.getUserId());
        List<String> tags = getTagNamesByNoteId(id);
        List<Long> tagIds = getTagIdsByNoteId(id);

        NoteDetailResponse response =
                NoteDetailResponse.builder()
                        .id(note.getId())
                        .title(note.getTitle())
                        .coverUrl(note.getCoverUrl())
                        .destination(note.getDestination())
                        .summary(note.getSummary())
                        .content(note.getContent())
                        .authorId(note.getUserId())
                        .authorNickname(author != null ? author.getNickname() : "未知用户")
                        .authorAvatarUrl(author != null ? author.getAvatarUrl() : null)
                        .tags(tags)
                        .tagIds(tagIds)
                        .viewCount(note.getViewCount())
                        .likeCount(note.getLikeCount())
                        .favoriteCount(note.getFavoriteCount())
                        .commentCount(note.getCommentCount())
                        .status(note.getStatus())
                        .createTime(
                                note.getCreateTime() != null
                                        ? note.getCreateTime().format(FMT)
                                        : null)
                        .updateTime(
                                note.getUpdateTime() != null
                                        ? note.getUpdateTime().format(FMT)
                                        : null)
                        .build();

        // 当前用户是否已点赞/收藏
        try {
            Long userId = LoginUserUtils.getUserId();
            response.setLiked(
                    noteLikeMapper.selectCount(
                                    new LambdaQueryWrapper<NoteLike>()
                                            .eq(NoteLike::getNoteId, id)
                                            .eq(NoteLike::getUserId, userId))
                            > 0);
            response.setFavorited(
                    noteFavoriteMapper.selectCount(
                                    new LambdaQueryWrapper<NoteFavorite>()
                                            .eq(NoteFavorite::getNoteId, id)
                                            .eq(NoteFavorite::getUserId, userId))
                            > 0);
        } catch (Exception e) {
            // 未登录用户无法获取互动状态，默认为 false
            response.setLiked(false);
            response.setFavorited(false);
        }

        return response;
    }

    @Override
    @Transactional
    public void update(Long id, UpdateNoteRequest request) {
        Note note = requireNote(id);
        Long userId = LoginUserUtils.getUserId();

        if (!note.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "仅作者可编辑此游记");
        }

        // 通过 使用BeanUtil 工具 优化代码
        BeanUtils.copyProperties(request, note);
        note.setUpdateTime(LocalDateTime.now());
        noteMapper.updateById(note);
        clearHomeCache();

        // 重建标签关联
        noteTagMapper.delete(new LambdaQueryWrapper<NoteTag>().eq(NoteTag::getNoteId, id));
        if (request.getTagIds() != null && !request.getTagIds().isEmpty()) {
            saveNoteTags(id, request.getTagIds());
        }
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Note note = requireNote(id);
        Long userId = LoginUserUtils.getUserId();

        if (!note.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "仅作者可删除此游记");
        }

        // 软删除：先设置业务删除状态，再交给 MyBatis-Plus 更新逻辑删除字段。
        note.setStatus(NoteStatusEnum.DELETED.ordinal());
        note.setUpdateTime(LocalDateTime.now());
        noteMapper.updateById(note);
        noteMapper.deleteById(id);
        clearHomeCache();
    }

    private void clearHomeCache() {
        try {
            stringRedisTemplate.delete(cacheKeyPrefix + ":home");
        } catch (Exception ignored) {
            // 缓存清理失败不影响主流程
        }
    }

    // ---------- private helpers ----------

    private Note requireNote(Long id) {
        Note note = noteMapper.selectById(id);
        if (note == null || note.getStatus() == NoteStatusEnum.DELETED.ordinal()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "游记不存在");
        }
        return note;
    }

    private NoteListItemResponse toListItemResponse(Note note) {
        SysUser author = userMapper.selectById(note.getUserId());
        return NoteListItemResponse.builder()
                .id(note.getId())
                .title(note.getTitle())
                .coverUrl(note.getCoverUrl())
                .destination(note.getDestination())
                .summary(note.getSummary())
                .authorId(note.getUserId())
                .authorNickname(author != null ? author.getNickname() : "未知用户")
                .authorAvatarUrl(author != null ? author.getAvatarUrl() : null)
                .tags(getTagNamesByNoteId(note.getId()))
                .likeCount(note.getLikeCount())
                .favoriteCount(note.getFavoriteCount())
                .commentCount(note.getCommentCount())
                .createTime(note.getCreateTime() != null ? note.getCreateTime().format(FMT) : null)
                .build();
    }

    private List<String> getTagNamesByNoteId(Long noteId) {
        List<NoteTag> noteTags =
                noteTagMapper.selectList(
                        new LambdaQueryWrapper<NoteTag>().eq(NoteTag::getNoteId, noteId));
        if (noteTags.isEmpty()) return Collections.emptyList();
        return noteTags.stream()
                .map(nt -> tagMapper.selectById(nt.getTagId()))
                .filter(tag -> tag != null && tag.getStatus() == 1)
                .map(Tag::getName)
                .collect(Collectors.toList());
    }

    private List<Long> getTagIdsByNoteId(Long noteId) {
        List<NoteTag> noteTags =
                noteTagMapper.selectList(
                        new LambdaQueryWrapper<NoteTag>().eq(NoteTag::getNoteId, noteId));
        if (noteTags.isEmpty()) return Collections.emptyList();
        return noteTags.stream().map(NoteTag::getTagId).distinct().collect(Collectors.toList());
    }

    private List<Long> getNoteIdsByTagId(Long tagId) {
        List<NoteTag> noteTags =
                noteTagMapper.selectList(
                        new LambdaQueryWrapper<NoteTag>().eq(NoteTag::getTagId, tagId));
        return noteTags.stream().map(NoteTag::getNoteId).distinct().collect(Collectors.toList());
    }

    private void saveNoteTags(Long noteId, List<Long> tagIds) {
        LocalDateTime now = LocalDateTime.now();
        List<NoteTag> list =
                tagIds.stream()
                        .map(
                                tagId ->
                                        NoteTag.builder()
                                                .noteId(noteId)
                                                .tagId(tagId)
                                                .createTime(now)
                                                .build())
                        .collect(Collectors.toList());
        noteTagMapper.insert(list);
    }
}
