package com.sora.aitravel.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sora.aitravel.common.utils.DateTimeUtils;
import com.sora.aitravel.dto.response.TagResponse;
import com.sora.aitravel.entity.Tag;
import com.sora.aitravel.mapper.TagMapper;
import com.sora.aitravel.service.TagService;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 标签服务实现类。
 *
 * <p>提供标签列表查询功能，支持按类型筛选已启用的标签。
 */
@Service
@RequiredArgsConstructor
public class TagServiceImpl implements TagService {

    private final TagMapper tagMapper;

    @Override
    public List<TagResponse> list(Integer type) {
        LambdaQueryWrapper<Tag> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Tag::getStatus, 1);
        if (type != null) {
            wrapper.eq(Tag::getType, type);
        }
        wrapper.orderByAsc(Tag::getType).orderByAsc(Tag::getId);

        return tagMapper.selectList(wrapper).stream()
                .map(this::toTagResponse)
                .collect(Collectors.toList());
    }

    private TagResponse toTagResponse(Tag tag) {
        return new TagResponse(
                tag.getId(),
                tag.getName(),
                tag.getType(),
                tag.getStatus(),
                DateTimeUtils.format(tag.getCreateTime()));
    }
}
