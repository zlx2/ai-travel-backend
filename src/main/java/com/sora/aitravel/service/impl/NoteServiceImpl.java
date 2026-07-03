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

    /**
     * 分页查询已发布的游记列表，支持关键字、目的地、标签筛选和排序。
     *
     * @param pageNum 页码（从 1 开始）
     * @param pageSize 每页条数
     * @param keyword 搜索关键词（匹配标题或摘要）
     * @param destination 目的地精确筛选
     * @param tagId 标签 ID 筛选（通过 note_tag 关联表子查询）
     * @param sort 排序方式："hot" 按点赞数降序，其他按创建时间降序
     * @return 分页结果（含游记列表、总数、页码、页大小）
     */
    @Override
    public PageResult<NoteListItemResponse> list(
            Integer pageNum,
            Integer pageSize,
            String keyword,
            String destination,
            Long tagId,
            String sort) {

        // 构建 MyBatis-Plus 条件构造器
        LambdaQueryWrapper<Note> wrapper = new LambdaQueryWrapper<>();
        // 仅展示已发布的游记（status = PUBLISHED 的枚举序号）
        wrapper.eq(Note::getStatus, NoteStatusEnum.PUBLISHED.ordinal());

        // 关键字筛选：标题或摘要中包含关键词（OR 关系）
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w.like(Note::getTitle, keyword).or().like(Note::getSummary, keyword));
        }
        // 目的地精确筛选
        if (destination != null && !destination.isBlank()) {
            wrapper.eq(Note::getDestination, destination);
        }
        // 标签筛选：先从 note_tag 关联表查出匹配的游记 ID 列表，再用 IN 条件过滤
        if (tagId != null && tagId > 0) {
            List<Long> noteIds = getNoteIdsByTagId(tagId);
            // 没有匹配游记时直接返回空结果，避免执行无意义的 SQL
            if (noteIds.isEmpty()) {
                return new PageResult<>(Collections.emptyList(), 0L, pageNum, pageSize);
            }
            wrapper.in(Note::getId, noteIds);
        }
        // 排序策略："hot" 按点赞数降序，默认按创建时间降序（最新优先）
        if ("hot".equals(sort)) {
            wrapper.orderByDesc(Note::getLikeCount);
        } else {
            wrapper.orderByDesc(Note::getCreateTime);
        }

        // 执行分页查询
        Page<Note> page = new Page<>(pageNum, pageSize);
        Page<Note> result = noteMapper.selectPage(page, wrapper);

        // 将 Note 实体转换为 NoteListItemResponse（含作者信息和标签）
        List<NoteListItemResponse> list =
                result.getRecords().stream()
                        .map(this::toListItemResponse)
                        .collect(Collectors.toList());

        return new PageResult<>(list, result.getTotal(), pageNum, pageSize);
    }

    /**
     * 分页查询当前登录用户的游记列表（含草稿），排除已删除的游记。
     *
     * @param pageNum 页码（从 1 开始）
     * @param pageSize 每页条数
     * @param status 状态筛选（可选），如草稿/已发布
     * @return 分页结果
     */
    @Override
    public PageResult<NoteListItemResponse> listMine(
            Integer pageNum, Integer pageSize, Integer status) {
        // 获取当前登录用户 ID（SaToken 上下文）
        Long userId = LoginUserUtils.getUserId();

        LambdaQueryWrapper<Note> wrapper = new LambdaQueryWrapper<>();
        // 限定当前用户的游记
        wrapper.eq(Note::getUserId, userId);
        // 排除已删除的游记（DELETED 状态）
        wrapper.ne(Note::getStatus, NoteStatusEnum.DELETED.ordinal());
        // 可选：按指定状态筛选（如只看草稿或只看已发布）
        if (status != null) {
            wrapper.eq(Note::getStatus, status);
        }
        // 按更新时间降序排列（最近编辑的优先）
        wrapper.orderByDesc(Note::getUpdateTime);

        Page<Note> page = new Page<>(pageNum, pageSize);
        Page<Note> result = noteMapper.selectPage(page, wrapper);

        List<NoteListItemResponse> list =
                result.getRecords().stream()
                        .map(this::toListItemResponse)
                        .collect(Collectors.toList());

        return new PageResult<>(list, result.getTotal(), pageNum, pageSize);
    }

    /**
     * 创建新游记，并关联标签。创建后清除首页缓存。
     *
     * @param request 创建游记请求（含标题、封面、目的地、摘要、正文、状态、标签 ID 列表）
     * @return 新创建游记的 ID
     */
    @Override
    @Transactional
    public Long create(CreateNoteRequest request) {
        // 获取当前登录用户 ID
        Long userId = LoginUserUtils.getUserId();
        // 创建/更新时间戳
        LocalDateTime now = LocalDateTime.now();

        // 使用 Builder 模式构建 Note 实体
        Note note =
                Note.builder()
                        .userId(userId) // 作者 ID
                        .title(request.getTitle()) // 游记标题
                        .coverUrl(request.getCoverUrl()) // 封面图片 URL
                        .destination(request.getDestination()) // 目的地
                        .summary(request.getSummary()) // 摘要
                        .content(request.getContent()) // 正文内容
                        .status(request.getStatus()) // 状态（草稿/已发布）
                        .viewCount(0) // 浏览数初始化为 0
                        .likeCount(0) // 点赞数初始化为 0
                        .favoriteCount(0) // 收藏数初始化为 0
                        .commentCount(0) // 评论数初始化为 0
                        .createTime(now)
                        .updateTime(now)
                        .build();

        // 插入数据库，MyBatis-Plus 自动回填自增 ID
        noteMapper.insert(note);
        // 清除首页 Redis 缓存（游记列表变更需刷新）
        clearHomeCache();

        // 关联标签：若请求中携带了标签 ID 列表，则批量插入 note_tag 关联记录
        if (request.getTagIds() != null && !request.getTagIds().isEmpty()) {
            saveNoteTags(note.getId(), request.getTagIds());
        }

        return note.getId();
    }

    /**
     * 获取游记详情，包含作者信息、标签列表和当前用户的点赞/收藏状态。
     *
     * <p>草稿仅作者本人可查看，其他用户访问草稿将返回"游记不存在"。
     *
     * @param id 游记 ID
     * @return 游记详情响应（NoteDetailResponse）
     */
    @Override
    public NoteDetailResponse detail(Long id) {
        // 查询游记，不存在或已删除则抛异常
        Note note = requireNote(id);

        // 草稿权限校验：仅作者本人可查看草稿
        if (note.getStatus() == NoteStatusEnum.DRAFT.ordinal()) {
            try {
                Long userId = LoginUserUtils.getUserId();
                // 非作者访问草稿 → 返回"游记不存在"（不暴露草稿存在事实）
                if (!note.getUserId().equals(userId)) {
                    throw new BusinessException(ErrorCode.NOT_FOUND, "游记不存在");
                }
            } catch (BusinessException e) {
                throw e; // 业务异常直接上抛
            } catch (Exception e) {
                // 未登录等非业务异常也返回"游记不存在"
                throw new BusinessException(ErrorCode.NOT_FOUND, "游记不存在");
            }
        }

        // 查询作者信息（昵称、头像）
        SysUser author = userMapper.selectById(note.getUserId());
        // 查询游记关联的标签名称列表
        List<String> tags = getTagNamesByNoteId(id);
        // 查询游记关联的标签 ID 列表（编辑时回显已选标签）
        List<Long> tagIds = getTagIdsByNoteId(id);

        // 构建详情响应对象
        NoteDetailResponse response =
                NoteDetailResponse.builder()
                        .id(note.getId())
                        .title(note.getTitle())
                        .coverUrl(note.getCoverUrl())
                        .destination(note.getDestination())
                        .summary(note.getSummary())
                        .content(note.getContent())
                        .authorId(note.getUserId())
                        // 作者昵称：有则显示，无则兜底"未知用户"
                        .authorNickname(author != null ? author.getNickname() : "未知用户")
                        .authorAvatarUrl(author != null ? author.getAvatarUrl() : null)
                        .tags(tags) // 标签名称列表
                        .tagIds(tagIds) // 标签 ID 列表
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

        // 查询当前登录用户是否已对该游记点赞/收藏
        try {
            Long userId = LoginUserUtils.getUserId();
            // 点赞状态：查询 note_like 表是否存在当前用户+当前游记的记录
            response.setLiked(
                    noteLikeMapper.selectCount(
                                    new LambdaQueryWrapper<NoteLike>()
                                            .eq(NoteLike::getNoteId, id)
                                            .eq(NoteLike::getUserId, userId))
                            > 0);
            // 收藏状态：查询 note_favorite 表是否存在当前用户+当前游记的记录
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

    /**
     * 更新游记内容和标签。仅作者可操作，更新后清除首页缓存。
     *
     * <p>标签采用"先删后插"策略：先删除旧关联记录，再插入新的关联。
     *
     * @param id 游记 ID
     * @param request 更新请求（标题、封面、目的地、摘要、正文、状态、标签 ID 列表）
     */
    @Override
    @Transactional
    public void update(Long id, UpdateNoteRequest request) {
        // 查询游记，不存在或已删除则抛异常
        Note note = requireNote(id);
        // 获取当前用户 ID
        Long userId = LoginUserUtils.getUserId();

        // 权限校验：仅作者可编辑自己的游记
        if (!note.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "仅作者可编辑此游记");
        }

        // 使用 BeanUtils 将请求中的非空属性复制到实体（避免逐字段赋值）
        BeanUtils.copyProperties(request, note);
        // 更新时间戳
        note.setUpdateTime(LocalDateTime.now());
        // 更新数据库
        noteMapper.updateById(note);
        // 清除首页 Redis 缓存
        clearHomeCache();

        // 重建标签关联：先删除旧的 note_tag 记录
        noteTagMapper.delete(new LambdaQueryWrapper<NoteTag>().eq(NoteTag::getNoteId, id));
        // 再插入新的标签关联
        if (request.getTagIds() != null && !request.getTagIds().isEmpty()) {
            saveNoteTags(id, request.getTagIds());
        }
    }

    /**
     * 软删除游记（仅作者可操作）。先设置业务 DELETED 状态，再更新逻辑删除字段。
     *
     * @param id 游记 ID
     */
    @Override
    @Transactional
    public void delete(Long id) {
        // 查询游记，不存在或已删除则抛异常
        Note note = requireNote(id);
        // 获取当前用户 ID
        Long userId = LoginUserUtils.getUserId();

        // 权限校验：仅作者可删除自己的游记
        if (!note.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "仅作者可删除此游记");
        }

        // 软删除步骤 1：设置业务状态为 DELETED，记录更新时间
        note.setStatus(NoteStatusEnum.DELETED.ordinal());
        note.setUpdateTime(LocalDateTime.now());
        noteMapper.updateById(note);
        // 软删除步骤 2：调用 deleteById 触发 MyBatis-Plus 的逻辑删除（更新 deleted 字段）
        noteMapper.deleteById(id);
        // 清除首页 Redis 缓存
        clearHomeCache();
    }

    /** 清除首页 Redis 缓存。游记增删改时调用，确保首页数据及时刷新。 缓存清理失败不影响主流程（仅记录异常，不抛出）。 */
    private void clearHomeCache() {
        try {
            // 删除 key = cacheKeyPrefix + ":home" 的缓存项
            stringRedisTemplate.delete(cacheKeyPrefix + ":home");
        } catch (Exception ignored) {
            // 缓存清理失败不影响主流程
        }
    }

    // ---------- private helpers ----------

    /**
     * 查询游记并校验存在性。不存在或已删除时抛出 NOT_FOUND 业务异常。
     *
     * @param id 游记 ID
     * @return 查询到的 Note 实体
     */
    private Note requireNote(Long id) {
        Note note = noteMapper.selectById(id);
        // 不存在或已删除状态 → 抛出"游记不存在"异常
        if (note == null || note.getStatus() == NoteStatusEnum.DELETED.ordinal()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "游记不存在");
        }
        return note;
    }

    /**
     * 将 Note 实体转换为列表项响应（含作者昵称、头像和标签名称列表）。
     *
     * @param note Note 实体
     * @return NoteListItemResponse 列表项 DTO
     */
    private NoteListItemResponse toListItemResponse(Note note) {
        // 查询作者信息
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
                .tags(getTagNamesByNoteId(note.getId())) // 查询关联标签名称列表
                .likeCount(note.getLikeCount())
                .favoriteCount(note.getFavoriteCount())
                .commentCount(note.getCommentCount())
                .createTime(note.getCreateTime() != null ? note.getCreateTime().format(FMT) : null)
                .build();
    }

    /**
     * 根据游记 ID 查询关联的标签名称列表。
     *
     * @param noteId 游记 ID
     * @return 标签名称列表（仅已启用的标签）
     */
    private List<String> getTagNamesByNoteId(Long noteId) {
        // 查询 note_tag 关联表中该游记的所有记录
        List<NoteTag> noteTags =
                noteTagMapper.selectList(
                        new LambdaQueryWrapper<NoteTag>().eq(NoteTag::getNoteId, noteId));
        if (noteTags.isEmpty()) return Collections.emptyList();
        return noteTags.stream()
                .map(nt -> tagMapper.selectById(nt.getTagId())) // 根据 tagId 查 Tag 实体
                .filter(tag -> tag != null && tag.getStatus() == 1) // 仅保留已启用的标签
                .map(Tag::getName) // 提取标签名称
                .collect(Collectors.toList());
    }

    /**
     * 根据游记 ID 查询关联的标签 ID 列表（去重）。
     *
     * @param noteId 游记 ID
     * @return 标签 ID 列表
     */
    private List<Long> getTagIdsByNoteId(Long noteId) {
        List<NoteTag> noteTags =
                noteTagMapper.selectList(
                        new LambdaQueryWrapper<NoteTag>().eq(NoteTag::getNoteId, noteId));
        if (noteTags.isEmpty()) return Collections.emptyList();
        // 提取 tagId 并去重
        return noteTags.stream().map(NoteTag::getTagId).distinct().collect(Collectors.toList());
    }

    /**
     * 根据标签 ID 查询关联的游记 ID 列表（去重），用于按标签筛选游记。
     *
     * @param tagId 标签 ID
     * @return 游记 ID 列表
     */
    private List<Long> getNoteIdsByTagId(Long tagId) {
        // 查询 note_tag 关联表中该标签的所有记录
        List<NoteTag> noteTags =
                noteTagMapper.selectList(
                        new LambdaQueryWrapper<NoteTag>().eq(NoteTag::getTagId, tagId));
        // 提取 noteId 并去重
        return noteTags.stream().map(NoteTag::getNoteId).distinct().collect(Collectors.toList());
    }

    /**
     * 批量保存游记-标签关联记录。
     *
     * @param noteId 游记 ID
     * @param tagIds 标签 ID 列表
     */
    private void saveNoteTags(Long noteId, List<Long> tagIds) {
        LocalDateTime now = LocalDateTime.now();
        // 将每个 tagId 构建为 NoteTag 关联实体
        List<NoteTag> list =
                tagIds.stream()
                        .map(
                                tagId ->
                                        NoteTag.builder()
                                                .noteId(noteId) // 游记 ID
                                                .tagId(tagId) // 标签 ID
                                                .createTime(now) // 创建时间
                                                .build())
                        .collect(Collectors.toList());
        // 批量插入 note_tag 关联记录
        noteTagMapper.insert(list);
    }
}
