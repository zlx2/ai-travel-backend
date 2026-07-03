package com.sora.aitravel.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sora.aitravel.common.utils.DateTimeUtils;
import com.sora.aitravel.dto.response.DestinationResponse;
import com.sora.aitravel.dto.response.HomeResponse;
import com.sora.aitravel.dto.response.NoteListItemResponse;
import com.sora.aitravel.dto.response.TagResponse;
import com.sora.aitravel.entity.Destination;
import com.sora.aitravel.entity.Note;
import com.sora.aitravel.entity.NoteTag;
import com.sora.aitravel.entity.SysUser;
import com.sora.aitravel.entity.Tag;
import com.sora.aitravel.mapper.DestinationMapper;
import com.sora.aitravel.mapper.NoteMapper;
import com.sora.aitravel.mapper.NoteTagMapper;
import com.sora.aitravel.mapper.SysUserMapper;
import com.sora.aitravel.mapper.TagMapper;
import com.sora.aitravel.service.HomeService;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 首页服务实现
 * 提供首页聚合数据，包含热门目的地、热门笔记、热门标签等
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HomeServiceImpl implements HomeService {
    private static final int HOT_DESTINATION_LIMIT = 6;
    private static final int HOT_NOTE_LIMIT = 6;
    private static final int HOT_TAG_LIMIT = 12;

    /** 首页缓存有效期：演示项目使用 3 天，避免频繁回源查询。 */
    private static final Duration HOME_CACHE_TTL = Duration.ofDays(3);

    private final DestinationMapper destinationMapper;
    private final NoteMapper noteMapper;
    private final NoteTagMapper noteTagMapper;
    private final TagMapper tagMapper;
    private final SysUserMapper userMapper;
    private final ObjectMapper objectMapper;
    private final StringRedisTemplate stringRedisTemplate;

    @Value("${app.cache.key-prefix:plango:dev}")
    private String cacheKeyPrefix;

    /**
     * 聚合首页数据
     * 优先从缓存获取，缓存不存在时从数据库查询并写入缓存
     *
     * @return 首页聚合响应
     */
    @Override
    public HomeResponse aggregate() {
        HomeResponse cached = getHomeFromCache();
        if (cached != null) {
            return cached;
        }

        HomeResponse fresh = queryHomeFromDb();
        putHomeToCache(fresh);
        return fresh;
    }

    /**
     * 从缓存获取首页数据
     *
     * @return 首页响应，如果缓存不存在或读取失败则返回null
     */
    private HomeResponse getHomeFromCache() {
        try {
            String json = stringRedisTemplate.opsForValue().get(homeCacheKey());
            if (!StringUtils.hasText(json)) {
                return null;
            }
            return objectMapper.readValue(json, HomeResponse.class);
        } catch (Exception e) {
            log.warn("读取首页 Redis 缓存失败，降级查询数据库", e);
            return null;
        }
    }

    /**
     * 将首页数据写入缓存
     *
     * @param response 首页响应
     */
    private void putHomeToCache(HomeResponse response) {
        try {
            String json = objectMapper.writeValueAsString(response);
            stringRedisTemplate.opsForValue().set(homeCacheKey(), json, HOME_CACHE_TTL);
        } catch (Exception e) {
            log.warn("写入首页 Redis 缓存失败，忽略缓存", e);
        }
    }

    /**
     * 获取首页缓存键
     *
     * @return 缓存键
     */
    private String homeCacheKey() {
        return cacheKeyPrefix + ":home";
    }

    /**
     * 从数据库查询首页数据
     *
     * @return 首页聚合响应
     */
    private HomeResponse queryHomeFromDb() {
        List<DestinationResponse> destinations =
                destinationMapper
                        .selectList(
                                new LambdaQueryWrapper<Destination>()
                                        .eq(Destination::getStatus, 1)
                                        .orderByDesc(Destination::getHeat)
                                        .orderByDesc(Destination::getCreateTime)
                                        .last("limit " + HOT_DESTINATION_LIMIT))
                        .stream()
                        .map(this::toDestinationResponse)
                        .collect(Collectors.toList());

        List<Note> notes =
                noteMapper.selectList(
                        new LambdaQueryWrapper<Note>()
                                .eq(Note::getStatus, 1)
                                .orderByDesc(Note::getLikeCount)
                                .orderByDesc(Note::getFavoriteCount)
                                .orderByDesc(Note::getCreateTime)
                                .last("limit " + HOT_NOTE_LIMIT));

        Map<Long, SysUser> users =
                notes.stream()
                        .map(Note::getUserId)
                        .filter(id -> id != null)
                        .distinct()
                        .collect(
                                Collectors.collectingAndThen(Collectors.toList(), this::loadUsers));

        List<NoteListItemResponse> hotNotes =
                notes.stream()
                        .map(note -> toNoteListItemResponse(note, users.get(note.getUserId())))
                        .collect(Collectors.toList());

        List<TagResponse> hotTags =
                tagMapper
                        .selectList(
                                new LambdaQueryWrapper<Tag>()
                                        .eq(Tag::getStatus, 1)
                                        .orderByAsc(Tag::getType)
                                        .orderByAsc(Tag::getId)
                                        .last("limit " + HOT_TAG_LIMIT))
                        .stream()
                        .map(this::toTagResponse)
                        .collect(Collectors.toList());

        return HomeResponse.builder()
                .hotDestinations(destinations)
                .hotNotes(hotNotes)
                .hotTags(hotTags)
                .build();
    }

    /**
     * 批量加载用户信息
     *
     * @param userIds 用户ID列表
     * @return 用户ID到用户实体的映射
     */
    private Map<Long, SysUser> loadUsers(List<Long> userIds) {
        if (userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return userMapper.selectBatchIds(userIds).stream()
                .collect(
                        Collectors.toMap(
                                SysUser::getId, Function.identity(), (left, right) -> left));
    }

    /**
     * 将目的地实体转换为响应对象
     *
     * @param destination 目的地实体
     * @return 目的地响应
     */
    private DestinationResponse toDestinationResponse(Destination destination) {
        return DestinationResponse.builder()
                .id(destination.getId())
                .name(destination.getName())
                .province(destination.getProvince())
                .city(destination.getCity())
                .longitude(destination.getLongitude())
                .latitude(destination.getLatitude())
                .coverUrl(destination.getCoverUrl())
                .description(destination.getDescription())
                .tags(parseTags(destination.getTagsJson()))
                .heat(destination.getHeat())
                .status(destination.getStatus())
                .createTime(DateTimeUtils.format(destination.getCreateTime()))
                .build();
    }

    /**
     * 将笔记实体转换为列表项响应对象
     *
     * @param note   笔记实体
     * @param author 作者用户实体
     * @return 笔记列表项响应
     */
    private NoteListItemResponse toNoteListItemResponse(Note note, SysUser author) {
        List<String> tags =
                noteTagMapper
                        .selectList(
                                new LambdaQueryWrapper<NoteTag>()
                                        .eq(NoteTag::getNoteId, note.getId()))
                        .stream()
                        .map(nt -> tagMapper.selectById(nt.getTagId()))
                        .filter(tag -> tag != null && tag.getStatus() == 1)
                        .map(Tag::getName)
                        .collect(Collectors.toList());

        return NoteListItemResponse.builder()
                .id(note.getId())
                .title(note.getTitle())
                .coverUrl(note.getCoverUrl())
                .destination(note.getDestination())
                .summary(note.getSummary())
                .authorId(note.getUserId())
                .authorNickname(author == null ? null : author.getNickname())
                .authorAvatarUrl(author == null ? null : author.getAvatarUrl())
                .tags(tags)
                .likeCount(note.getLikeCount())
                .favoriteCount(note.getFavoriteCount())
                .commentCount(note.getCommentCount())
                .createTime(DateTimeUtils.format(note.getCreateTime()))
                .build();
    }

    /**
     * 将标签实体转换为响应对象
     *
     * @param tag 标签实体
     * @return 标签响应
     */
    private TagResponse toTagResponse(Tag tag) {
        return TagResponse.builder()
                .id(tag.getId())
                .name(tag.getName())
                .type(tag.getType())
                .status(tag.getStatus())
                .createTime(DateTimeUtils.format(tag.getCreateTime()))
                .build();
    }

    /**
     * 解析标签JSON字符串
     *
     * @param tagsJson 标签JSON字符串
     * @return 标签列表
     */
    private List<String> parseTags(String tagsJson) {
        if (tagsJson == null || tagsJson.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(tagsJson, new TypeReference<List<String>>() {});
        } catch (Exception ignored) {
            return Collections.emptyList();
        }
    }
}