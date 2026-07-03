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

    /**
     * 查询标签列表。支持按类型筛选，仅返回已启用的标签。
     *
     * @param type 标签类型（可选）：1-目的地标签，2-游记标签，3-偏好标签。null 返回全部。
     * @return 标签列表（TagResponse）
     */
    @Override
    public List<TagResponse> list(Integer type) {
        // 构建 MyBatis-Plus 条件构造器
        LambdaQueryWrapper<Tag> wrapper = new LambdaQueryWrapper<>();
        // 仅查询已启用的标签（status=1）
        wrapper.eq(Tag::getStatus, 1);
        // 可选：按标签类型精确筛选
        if (type != null) {
            wrapper.eq(Tag::getType, type);
        }
        // 排序：先按类型升序，再按 ID 升序（保证同类型标签排列稳定）
        wrapper.orderByAsc(Tag::getType).orderByAsc(Tag::getId);

        // 执行查询并转换为 DTO
        return tagMapper.selectList(wrapper).stream()
                .map(this::toTagResponse)      // Tag 实体 → TagResponse DTO
                .collect(Collectors.toList());
    }

    /**
     * 将 Tag 实体转换为 TagResponse DTO。
     *
     * @param tag Tag 实体
     * @return TagResponse（含 ID、名称、类型、状态、创建时间）
     */
    private TagResponse toTagResponse(Tag tag) {
        return new TagResponse(
                tag.getId(),                   // 标签 ID
                tag.getName(),                 // 标签名称
                tag.getType(),                 // 标签类型（1=目的地, 2=游记, 3=偏好）
                tag.getStatus(),               // 状态（1=启用）
                DateTimeUtils.format(tag.getCreateTime())); // 格式化创建时间
    }
}
