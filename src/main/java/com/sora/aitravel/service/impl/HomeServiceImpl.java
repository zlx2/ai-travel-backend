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
import com.sora.aitravel.entity.SysUser;
import com.sora.aitravel.entity.Tag;
import com.sora.aitravel.mapper.DestinationMapper;
import com.sora.aitravel.mapper.NoteMapper;
import com.sora.aitravel.mapper.SysUserMapper;
import com.sora.aitravel.mapper.TagMapper;
import com.sora.aitravel.service.HomeService;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class HomeServiceImpl implements HomeService {
    private static final int HOT_DESTINATION_LIMIT = 6;
    private static final int HOT_NOTE_LIMIT = 3;
    private static final int HOT_TAG_LIMIT = 12;

    private final DestinationMapper destinationMapper;
    private final NoteMapper noteMapper;
    private final TagMapper tagMapper;
    private final SysUserMapper userMapper;
    private final ObjectMapper objectMapper;

    @Override
    public HomeResponse aggregate() {
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

        return new HomeResponse(destinations, hotNotes, hotTags);
    }

    private Map<Long, SysUser> loadUsers(List<Long> userIds) {
        if (userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return userMapper.selectBatchIds(userIds).stream()
                .collect(
                        Collectors.toMap(
                                SysUser::getId, Function.identity(), (left, right) -> left));
    }

    private DestinationResponse toDestinationResponse(Destination destination) {
        return new DestinationResponse(
                destination.getId(),
                destination.getName(),
                destination.getProvince(),
                destination.getCity(),
                destination.getLongitude(),
                destination.getLatitude(),
                destination.getCoverUrl(),
                destination.getDescription(),
                parseTags(destination.getTagsJson()),
                destination.getHeat(),
                destination.getStatus(),
                DateTimeUtils.format(destination.getCreateTime()));
    }

    private NoteListItemResponse toNoteListItemResponse(Note note, SysUser author) {
        return new NoteListItemResponse(
                note.getId(),
                note.getTitle(),
                note.getCoverUrl(),
                note.getDestination(),
                note.getSummary(),
                note.getUserId(),
                author == null ? null : author.getNickname(),
                author == null ? null : author.getAvatarUrl(),
                note.getLikeCount(),
                note.getFavoriteCount(),
                note.getCommentCount(),
                DateTimeUtils.format(note.getCreateTime()));
    }

    private TagResponse toTagResponse(Tag tag) {
        return new TagResponse(
                tag.getId(),
                tag.getName(),
                tag.getType(),
                tag.getStatus(),
                DateTimeUtils.format(tag.getCreateTime()));
    }

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
