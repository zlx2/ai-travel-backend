package com.sora.aitravel.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 文件资源实体类。
 *
 * <p>对应数据库表 {@code file_resource}，存储用户上传的文件资源信息，包括文件所属 用户、业务类型、文件名、对象存储 key、访问 URL、MIME 类型和文件大小等。
 * 一期只支持 avatar（头像）和 note_cover（游记封面）两种业务类型。 该表通过逻辑删除标记（deleted）实现软删除。
 *
 * <table border="1">
 *   <caption>字段与数据库列映射</caption>
 *   <tr><th>字段</th><th>数据库列</th><th>说明</th></tr>
 *   <tr><td>id</td><td>id</td><td>主键，自增</td></tr>
 *   <tr><td>userId</td><td>user_id</td><td>上传用户 ID，关联 sys_user.id</td></tr>
 *   <tr><td>bizType</td><td>biz_type</td><td>业务类型：avatar（头像）或 note_cover（游记封面）</td></tr>
 *   <tr><td>fileName</td><td>file_name</td><td>原始文件名</td></tr>
 *   <tr><td>objectKey</td><td>object_key</td><td>对象存储（OSS/S3）中的 key</td></tr>
 *   <tr><td>url</td><td>url</td><td>文件访问 URL</td></tr>
 *   <tr><td>contentType</td><td>content_type</td><td>文件 MIME 类型</td></tr>
 *   <tr><td>size</td><td>size</td><td>文件大小，单位字节</td></tr>
 *   <tr><td>status</td><td>status</td><td>状态：0-禁用，1-正常</td></tr>
 *   <tr><td>createTime</td><td>create_time</td><td>记录创建时间</td></tr>
 *   <tr><td>updateTime</td><td>update_time</td><td>记录更新时间</td></tr>
 *   <tr><td>deleted</td><td>deleted</td><td>逻辑删除标记：0-未删除，1-已删除</td></tr>
 * </table>
 */
@Data
@TableName("file_resource")
public class FileResource {
    /** 主键 ID，自增。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 上传用户 ID，关联 sys_user.id。 */
    private Long userId;

    /** 业务类型：一期只允许 avatar（头像）或 note_cover（游记封面）。 */
    private String bizType;

    /** 上传时的原始文件名。 */
    private String fileName;

    /** 对象存储（OSS/S3）中的唯一 key。 */
    private String objectKey;

    /** 文件对外访问的完整 URL。 */
    private String url;

    /** 文件 MIME 类型，如 image/jpeg、image/png。 */
    private String contentType;

    /** 文件大小，单位字节。 */
    private Long size;

    /** 状态：0=禁用，1=正常。 */
    private Integer status;

    /** 记录创建时间。 */
    private LocalDateTime createTime;

    /** 记录更新时间。 */
    private LocalDateTime updateTime;

    /** 逻辑删除标记：0=未删除，1=已删除。 */
    @TableLogic private Integer deleted;
}
