package com.sora.aitravel.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;

/** RabbitMQ 消息队列配置。 */
@Configuration
public class RabbitMqConfig {

    public static final String TRIP_DAY_GENERATE_EXCHANGE = "trip.day.generate.exchange";
    public static final String TRIP_DAY_GENERATE_QUEUE = "trip.day.generate.queue";
    public static final String TRIP_DAY_GENERATE_ROUTING_KEY = "trip.day.generate";

    @Bean
    public DirectExchange tripDayGenerateExchange() {
        return new DirectExchange(TRIP_DAY_GENERATE_EXCHANGE, true, false);
    }

    @Bean
    public Queue tripDayGenerateQueue() {
        return new Queue(TRIP_DAY_GENERATE_QUEUE, true);
    }

    @Bean
    public Binding tripDayGenerateBinding(Queue tripDayGenerateQueue, DirectExchange tripDayGenerateExchange) {
        return BindingBuilder.bind(tripDayGenerateQueue)
                .to(tripDayGenerateExchange)
                .with(TRIP_DAY_GENERATE_ROUTING_KEY);
    }

    @Bean
    public Jackson2JsonMessageConverter jackson2JsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
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
}
