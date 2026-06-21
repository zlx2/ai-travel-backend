package com.sora.aitravel.common.result;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 分页查询结果封装。
 * <p>
 * 统一分页接口的返回结构，包含数据列表、总数、当前页码和每页条数。
 * </p>
 *
 * @param <T> 列表中元素的数据类型
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PageResult<T> {
    /** 当前页码的数据列表。 */
    private List<T> list;
    /** 符合查询条件的数据总数。 */
    private Long total;
    /** 当前页码（从 1 开始）。 */
    private Integer pageNum;
    /** 每页显示的数据条数。 */
    private Integer pageSize;
}
