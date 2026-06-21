package com.sora.aitravel.common.result;

/**
 * 通用 ID 响应记录。
 * <p>
 * 用于创建资源后仅需返回 ID 的场景，如注册、创建游记等。
 * 使用 Java 14+ 的 record 特性，自动生成构造方法、getter、equals、hashCode 和 toString。
 * </p>
 *
 * @param id 资源唯一标识符
 */
public record IdResponse(Long id) {}
