package com.sora.aitravel.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sora.aitravel.dto.message.TripDayGenerateMessage;
import com.sora.aitravel.service.AiTripDayGenerateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.DefaultJackson2JavaTypeMapper;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** RabbitMQ 消息队列配置。 */
@Slf4j
@EnableRabbit
@Configuration
@RequiredArgsConstructor
public class RabbitMqConfig {

    public static final String TRIP_DAY_GENERATE_EXCHANGE = "trip.day.generate.exchange";
    public static final String TRIP_DAY_GENERATE_QUEUE = "trip.day.generate.queue";
    public static final String TRIP_DAY_GENERATE_ROUTING_KEY = "trip.day.generate";

    private final AiTripDayGenerateService aiTripDayGenerateService;

    @Bean
    public DirectExchange tripDayGenerateExchange() {
        return new DirectExchange(TRIP_DAY_GENERATE_EXCHANGE, true, false);
    }

    @Bean
    public Queue tripDayGenerateQueue() {
        return new Queue(TRIP_DAY_GENERATE_QUEUE, true);
    }

    @Bean
    public Binding tripDayGenerateBinding(
            Queue tripDayGenerateQueue, DirectExchange tripDayGenerateExchange) {
        return BindingBuilder.bind(tripDayGenerateQueue)
                .to(tripDayGenerateExchange)
                .with(TRIP_DAY_GENERATE_ROUTING_KEY);
    }

    @Bean
    public RabbitAdmin rabbitAdmin(ConnectionFactory connectionFactory) {
        RabbitAdmin rabbitAdmin = new RabbitAdmin(connectionFactory);
        rabbitAdmin.setAutoStartup(true);
        return rabbitAdmin;
    }

    @Bean
    public Jackson2JsonMessageConverter jackson2JsonMessageConverter(ObjectMapper objectMapper) {
        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter(objectMapper);
        DefaultJackson2JavaTypeMapper typeMapper = new DefaultJackson2JavaTypeMapper();
        typeMapper.setTrustedPackages("com.sora.aitravel.dto.message", "java.util", "java.lang");
        converter.setJavaTypeMapper(typeMapper);
        return converter;
    }

    @Bean
    public RabbitTemplate rabbitTemplate(
            ConnectionFactory connectionFactory, Jackson2JsonMessageConverter messageConverter) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(messageConverter);
        return rabbitTemplate;
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory, Jackson2JsonMessageConverter messageConverter) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(messageConverter);
        factory.setDefaultRequeueRejected(false);
        return factory;
    }

    @RabbitListener(queues = TRIP_DAY_GENERATE_QUEUE)
    public void consumeTripDayGenerate(TripDayGenerateMessage message) {
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