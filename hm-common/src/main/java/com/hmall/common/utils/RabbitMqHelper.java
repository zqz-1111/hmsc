package com.hmall.common.utils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

/**
 * RabbitMQ 工具类：封装普通消息、延迟消息、带回调确认的消息
 */
@Slf4j
@RequiredArgsConstructor
public class RabbitMqHelper {

    private final RabbitTemplate rabbitTemplate;

    /**
     * 发送普通消息
     * @param exchange   交换机
     * @param routingKey 路由键
     * @param msg        消息体
     */
    public void sendMessage(String exchange, String routingKey, Object msg) {
        rabbitTemplate.convertAndSend(exchange, routingKey, msg);
    }

    /**
     * 发送延迟消息
     * @param exchange   交换机
     * @param routingKey 路由键
     * @param msg        消息体
     * @param delay      延迟时间（毫秒）
     */
    public void sendDelayMessage(String exchange, String routingKey, Object msg, int delay) {
        rabbitTemplate.convertAndSend(exchange, routingKey, msg, message -> {
            message.getMessageProperties().setDelay(delay);
            return message;
        });
    }

    /**
     * 发送消息并等待 Publisher Confirm（同步阻塞）
     * @param exchange   交换机
     * @param routingKey 路由键
     * @param msg        消息体
     * @param maxRetries 最大重试次数
     */
    public void sendMessageWithConfirm(String exchange, String routingKey, Object msg, int maxRetries) {
        int retries = 0;
        while (retries < maxRetries) {
            try {
                CorrelationData correlationData = new CorrelationData();
                rabbitTemplate.convertAndSend(exchange, routingKey, msg, correlationData);
                // 阻塞等待 confirm 结果
                CorrelationData.Confirm confirm = correlationData.getFuture().get();
                if (confirm.isAck()) {
                    log.debug("消息发送成功，exchange={}, routingKey={}", exchange, routingKey);
                    return;
                } else {
                    log.warn("消息被 nack，exchange={}, routingKey={}, 原因={}", exchange, routingKey, confirm.getReason());
                }
            } catch (Exception e) {
                log.error("消息发送异常，exchange={}, routingKey={}, 第{}次重试", exchange, routingKey, retries + 1, e);
            }
            retries++;
        }
        throw new RuntimeException("消息发送失败，已重试" + maxRetries + "次，exchange=" + exchange + ", routingKey=" + routingKey);
    }
}
