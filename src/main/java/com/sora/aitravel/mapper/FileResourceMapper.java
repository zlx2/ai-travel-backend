package com.sora.aitravel.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sora.aitravel.entity.FileResource;
import org.apache.ibatis.annotations.Mapper;

/**
 * 文件资源 Mapper 接口。
 * <p>
 * 对应实体 {@link FileResource}，操作数据库表 {@code file_resource}。
 * 提供文件资源记录的增删改查能力，支持按用户 ID 和业务类型查询文件列表。
 * </p>
 */
@Mapper
public interface FileResourceMapper extends BaseMapper<FileResource> {}
