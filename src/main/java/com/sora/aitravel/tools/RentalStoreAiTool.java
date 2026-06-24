package com.sora.aitravel.tools;

import com.sora.aitravel.common.enums.ErrorCode;
import com.sora.aitravel.common.enums.RentalStoreUsageEnum;
import com.sora.aitravel.common.exception.BusinessException;
import com.sora.aitravel.dto.model.RentalStoreDTO;
import com.sora.aitravel.service.RentalStoreService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * 租车服务点 AI Tool。
 *
 * <p>这是给 Spring AI Function Calling 使用的正式工具入口。它只做参数校验和用途枚举转换，真实解析逻辑统一委托给 {@link
 * RentalStoreService}，避免 AI 调用路径和普通 HTTP 调用路径出现两套规则。
 */
@Component
@RequiredArgsConstructor
public class RentalStoreAiTool {

    private final RentalStoreService rentalStoreService;

    /**
     * 根据用户输入地点解析推荐取车或还车服务点。
     *
     * <p>工具返回结构化地点结果，供模型或工作流继续传给库存、价格、订单等后续节点；不要让模型把该结果误描述为平台自营门店。
     */
    @Tool(
            name = "resolveRentalStore",
            description =
                    """
                    根据用户输入的地点，查询该地点附近适合租车取车或还车的推荐服务点。
                    只返回一个结构化推荐地点，供后续库存、价格、异地还车、下单流程继续使用。
                    不负责查询车辆库存、报价、锁车、下单或支付。
                    usage 只能传 PICKUP 或 RETURN。
                    """)
    public RentalStoreDTO resolveRentalStore(
            @ToolParam(description = "用户输入的目标地点，例如：成都东站、杭州东站、西湖、萧山机场") String targetName,
            @ToolParam(description = "城市名称，例如：成都市、杭州市、上海市") String cityName,
            @ToolParam(description = "用途：PICKUP 表示取车点，RETURN 表示还车点") String usage) {
        if (targetName == null || targetName.isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "targetName 不能为空");
        }
        if (cityName == null || cityName.isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "cityName 不能为空");
        }

        return rentalStoreService.resolveRentalStore(
                targetName.trim(), cityName.trim(), RentalStoreUsageEnum.from(usage));
    }
}
