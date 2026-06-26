package com.sora.aitravel.consumer;

import com.sora.aitravel.config.RabbitMqConfig;
import com.sora.aitravel.dto.message.TripDayGenerateMessage;
import com.sora.aitravel.service.AiTripDayGenerateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/** 单日行程生成消息消费者。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TripDayGenerateConsumer {

    private final AiTripDayGenerateService aiTripDayGenerateService;

    @RabbitListener(queues = RabbitMqConfig.TRIP_DAY_GENERATE_QUEUE)
    public void consume(TripDayGenerateMessage message) {
        log.info(
                "收到单日行程生成消息，sessionId={}, dayNo={}, mode={}, requestId={}",
                message.sessionId(),
                message.dayNo(),
                message.requestMode(),
                message.requestId());
        aiTripDayGenerateService.generateDay(
                message.sessionId(),
                message.dayNo(),
                message.requestMode(),
                Boolean.TRUE.equals(message.forceRegenerate()));
    }
}
