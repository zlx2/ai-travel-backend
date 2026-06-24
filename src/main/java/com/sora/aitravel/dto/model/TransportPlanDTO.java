package com.sora.aitravel.dto.model;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 交通方案上下文。
 *
 * <p>用于把自驾、租车点、公共交通等建议传给行程生成节点。租车点只作为自驾游参考，不表示可下单。
 *
 * @param travelMode 推荐交通方式
 * @param pickupStore 推荐取车点，非自驾时可为空
 * @param returnStore 推荐还车点，非自驾时可为空
 * @param tips 交通提示
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TransportPlanDTO {

    private TravelModeDTO travelMode;
    private RentalStoreDTO pickupStore;
    private RentalStoreDTO returnStore;
    private List<String> tips;
}
