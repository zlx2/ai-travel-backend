package com.sora.aitravel.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 系统用户实体类。
 *
 * <p>对应数据库表 {@code sys_user}，存储系统注册用户信息，包括用户名、密码哈希、邮箱、昵称、
 * 头像地址、角色与状态等。用户可通过邮箱注册并登录系统，角色分为普通用户和管理员两种。 该表通过逻辑删除标记（deleted）实现软删除。
 *
 * <table border="1">
 *   <caption>字段与数据库列映射</caption>
 *   <tr><th>字段</th><th>数据库列</th><th>说明</th></tr>
 *   <tr><td>id</td><td>id</td><td>主键，自增</td></tr>
 *   <tr><td>username</td><td>username</td><td>登录用户名</td></tr>
 *   <tr><td>passwordHash</td><td>password_hash</td><td>BCrypt 加密后的密码</td></tr>
 *   <tr><td>email</td><td>email</td><td>邮箱地址</td></tr>
 *   <tr><td>nickname</td><td>nickname</td><td>用户昵称</td></tr>
 *   <tr><td>avatarUrl</td><td>avatar_url</td><td>头像文件 URL</td></tr>
 *   <tr><td>role</td><td>role</td><td>角色：1-普通用户，2-管理员</td></tr>
 *   <tr><td>status</td><td>status</td><td>状态：0-禁用，1-正常</td></tr>
 *   <tr><td>lastLoginTime</td><td>last_login_time</td><td>最后登录时间</td></tr>
 *   <tr><td>createTime</td><td>create_time</td><td>记录创建时间</td></tr>
 *   <tr><td>updateTime</td><td>update_time</td><td>记录更新时间</td></tr>
 *   <tr><td>deleted</td><td>deleted</td><td>逻辑删除标记：0-未删除，1-已删除</td></tr>
 * </table>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("sys_user")
public class SysUser {
    /** 主键 ID，自增。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 登录用户名，唯一。 */
    private String username;

    /** BCrypt 加密后的密码哈希值。 */
    private String passwordHash;

    /** 用户邮箱地址，用于登录和找回密码。 */
    private String email;

    /** 用户显示昵称。 */
    private String nickname;

    /** 头像文件 URL，若未设置则为空。 */
    private String avatarUrl;

    /** 用户角色：1=普通用户，2=管理员。 */
    private Integer role;

    /** 用户状态：0=禁用，1=正常。 */
    private Integer status;

    /** 最后登录时间，首次注册时为空。 */
    private LocalDateTime lastLoginTime;

    /** 记录创建时间。 */
    private LocalDateTime createTime;

    /** 记录更新时间。 */
    private LocalDateTime updateTime;

    /** MyBatis-Plus 逻辑删除标记：0=未删除，1=已删除。 */
    @TableLogic private Integer deleted;
}
