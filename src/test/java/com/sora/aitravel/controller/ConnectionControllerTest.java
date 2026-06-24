package com.sora.aitravel.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.sora.aitravel.common.result.R;
import com.sora.aitravel.dto.request.ConnectionCheckRequest;
import com.sora.aitravel.dto.response.ConnectionCheckResponse;
import org.junit.jupiter.api.Test;

class ConnectionControllerTest {

    private final ConnectionController controller = new ConnectionController();

    @Test
    void shouldEchoFrontendConnectionRequest() {
        R<ConnectionCheckResponse> result =
                controller.connect(new ConnectionCheckRequest("主页按钮联调"));

        assertThat(result.getCode()).isEqualTo(200);
        assertThat(result.getData().getMessage()).isEqualTo("后端已收到请求，前后端联通正常");
        assertThat(result.getData().getReceivedAction()).isEqualTo("主页按钮联调");
        assertThat(result.getData().getReceivedAt()).isNotNull();
    }
}
