package com.sora.aitravel.service;

import com.sora.aitravel.dto.model.RentalQuoteOptionDTO;
import com.sora.aitravel.dto.model.TravelRequirementDTO;
import com.sora.aitravel.dto.request.RentalContextPreviewRequest;
import com.sora.aitravel.dto.response.RentalContextPreviewResponse;
import com.sora.aitravel.dto.response.RentalQuotePreviewResponse;
import java.util.List;

/**
 * 租车报价服务接口
 * 提供租车报价预览、上下文预览、重新计算报价等业务操作
 */
public interface RentalQuoteService {

    /**
     * 预览租车上下文
     * 根据旅行需求和到达点预览租车上下文，包括推荐报价和取车方案
     *
     * @param request 租车上下文预览请求
     * @return 租车上下文预览响应
     */
    RentalContextPreviewResponse previewContext(RentalContextPreviewRequest request);

    /**
     * 预览租车报价
     * 根据旅行需求生成租车报价列表
     *
     * @param requirement 旅行需求DTO
     * @return 租车报价预览响应
     */
    RentalQuotePreviewResponse preview(TravelRequirementDTO requirement);

    /**
     * 重新计算报价
     * 根据当前需求重新计算报价，用于订单创建时的报价校验
     *
     * @param requirement   旅行需求DTO
     * @param selectedQuote 用户选中的报价
     * @return 重新计算后的报价选项
     */
    RentalQuoteOptionDTO recalculate(
            TravelRequirementDTO requirement, RentalQuoteOptionDTO selectedQuote);

    /**
     * 获取最近已订购的报价选项
     *
     * @param limit 返回数量限制
     * @return 最近订购的报价选项列表
     */
    List<RentalQuoteOptionDTO> latestOrderedOptions(int limit);
}