package com.sora.aitravel.service.impl;

import com.sora.aitravel.config.RabbitMqConfig;
import com.sora.aitravel.dto.message.TripDayGenerateMessage;
import com.sora.aitravel.service.TripDayGenerateMessageProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

/** 单日行程生成消息生产者实现。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TripDayGenerateMessageProducerImpl implements TripDayGenerateMessageProducer {

    private final RabbitTemplate rabbitTemplate;

    @Override
    public void send(TripDayGenerateMessage message) {
        rabbitTemplate.convertAndSend(RabbitMqConfig.TRIP_DAY_GENERATE_QUEUE, message);
        log.info(
                "已投递单日行程生成消息，sessionId={}, dayNo={}, mode={}",
                message.sessionId(),
                message.dayNo(),
                message.requestMode());
    }
}
