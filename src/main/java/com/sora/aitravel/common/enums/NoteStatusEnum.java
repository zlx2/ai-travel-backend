package com.sora.aitravel.common.enums;

/**
 * 游记状态枚举。
 * <p>
 * DRAFT = 0（草稿）、PUBLISHED = 1（已发布）、DELETED = 2（已删除）。
 * 一期没有审核流程，发布即公开。
 * </p>
 */
public enum NoteStatusEnum {
    /** 草稿。 */
    DRAFT,
    /** 已发布。 */
    PUBLISHED,
    /** 已删除。 */
    DELETED
}
