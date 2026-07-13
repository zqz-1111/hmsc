package com.hmall.common.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.retry.RepublishMessageRecoverer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MQ消费失败自动处理：将失败消息投递到 error 交换机，由专门的 error 队列兜底
 * 当 spring.rabbitmq.listener.simple.retry.enabled=true 时生效
 */
@Configuration
@ConditionalOnClass(RabbitTemplate.class)
@ConditionalOnProperty(name = "spring.rabbitmq.listener.simple.retry.enabled", havingValue = "true")
public class MqConsumeErrorAutoConfiguration {

    @Value("${spring.application.name}")
    private String applicationName;

    /**
     * error 交换机
     */
    @Bean
    public DirectExchange errorExchange() {
        return new DirectExchange("error.direct");
    }

    /**
     * error 队列，名为：微服务名.error.queue
     */
    @Bean
    public Queue errorQueue() {
        return new Queue(applicationName + ".error.queue");
    }

    /**
     * 绑定：队列 → 交换机，routingKey = 微服务名
     */
    @Bean
    public Binding errorBinding(Queue errorQueue, DirectExchange errorExchange) {
        return BindingBuilder.bind(errorQueue).to(errorExchange).with(applicationName);
    }

    /**
     * 消费失败时，将消息重新投递到 error 交换机
     */
    @Bean
    public RepublishMessageRecoverer republishMessageRecoverer(RabbitTemplate rabbitTemplate) {
        return new RepublishMessageRecoverer(rabbitTemplate, "error.direct");
    }
}
