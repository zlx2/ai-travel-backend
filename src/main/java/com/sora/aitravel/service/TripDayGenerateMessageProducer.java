package com.sora.aitravel.service;

import com.sora.aitravel.dto.message.TripDayGenerateMessage;

/** 单日行程生成消息生产者。 */
public interface TripDayGenerateMessageProducer {

    void send(TripDayGenerateMessage message);
}
