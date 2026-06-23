package com.sora.aitravel.controller;

import com.sora.aitravel.common.result.R;
import com.sora.aitravel.dto.request.RentalStoreResolveRequest;
import com.sora.aitravel.dto.response.RentalStoreResolveResponse;
import com.sora.aitravel.service.RentalStoreService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 租车服务点控制器。
 *
 * <p>接口前缀：/api/rental/stores
 *
 * <p>当前只提供“根据目标地点解析推荐取还车点”的能力。返回结果是后续库存、价格、异店还车和订单流程的中间入参，
 * 不表示已经完成车辆查询、锁车或下单。
 */
@Slf4j
@RestController
@RequestMapping("/api/rental/stores")
public class RentalStoreController {

    private final RentalStoreService rentalStoreService;

    public RentalStoreController(RentalStoreService rentalStoreService) {
        this.rentalStoreService = rentalStoreService;
    }

    /**
     * 解析推荐租车服务点。
     *
     * @param request 目标地点、城市和取还车用途
     * @return 一个推荐服务点，供后续租车流程继续使用
     */
    @PostMapping("/resolve")
    public R<RentalStoreResolveResponse> resolve(
            @Valid @RequestBody RentalStoreResolveRequest request) {
        log.info(
                "解析租车服务点，targetName={}, cityName={}, usage={}",
                request.targetName(),
                request.cityName(),
                request.usage());
        return R.ok(rentalStoreService.resolveRentalStore(request));
    }
}
