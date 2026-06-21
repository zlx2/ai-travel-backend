package com.sora.aitravel.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("sys_user")
public class SysUser {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;
    private String passwordHash;
    private String email;
    private String nickname;
    private String avatarUrl;

    /** 1=普通用户，2=管理员。 */
    private Integer role;

    /** 0=禁用，1=正常。 */
    private Integer status;

    private LocalDateTime lastLoginTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    /** MyBatis-Plus 逻辑删除标记：0=未删除，1=已删除。 */
    @TableLogic private Integer deleted;
}
