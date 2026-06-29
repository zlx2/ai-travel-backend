package com.sora.aitravel.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlipayPagePayResponse {

    private Long orderId;
    private String orderNo;
    private String formHtml;
}
