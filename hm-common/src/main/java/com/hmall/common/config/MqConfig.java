package com.hmall.common.config;

import com.hmall.common.utils.RabbitMqHelper;
import com.hmall.common.utils.UserContext;
import org.aopalliance.aop.Advice;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MQ自动配置：生产者自动传递用户信息到消息Header，消费者自动从Header提取用户信息写入UserContext
 */
@Configuration
@ConditionalOnClass(RabbitTemplate.class)
public class MqConfig {

    @Bean
    public RabbitMqHelper rabbitMqHelper(RabbitTemplate rabbitTemplate) {
        return new RabbitMqHelper(rabbitTemplate);
    }

    /**
     * 生产者：发送消息前自动将UserContext用户信息写入Header
     */
    @Bean
    public static BeanPostProcessor rabbitTemplatePostProcessor() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
                if (bean instanceof RabbitTemplate) {
                    RabbitTemplate rabbitTemplate = (RabbitTemplate) bean;
                    rabbitTemplate.addBeforePublishPostProcessors(message -> {
                        Long userId = UserContext.getUser();
                        if (userId != null) {
                            message.getMessageProperties().setHeader("user-info", userId);
                        }
                        return message;
                    });
                }
                return bean;
            }
        };
    }

    /**
     * 消费者：监听器执行前自动从Header提取用户信息写入UserContext，执行后清理
     */
    @Bean
    public static BeanPostProcessor rabbitListenerContainerFactoryPostProcessor() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
                if (bean instanceof SimpleRabbitListenerContainerFactory) {
                    SimpleRabbitListenerContainerFactory factory = (SimpleRabbitListenerContainerFactory) bean;
                    Advice advice = new UserContextMethodInterceptor();
                    Advice[] existing = factory.getAdviceChain();
                    if (existing != null) {
                        Advice[] newChain = new Advice[existing.length + 1];
                        System.arraycopy(existing, 0, newChain, 0, existing.length);
                        newChain[existing.length] = advice;
                        factory.setAdviceChain(newChain);
                    } else {
                        factory.setAdviceChain(advice);
                    }
                }
                return bean;
            }
        };
    }

    /**
     * 用户上下文拦截器：从Message Header提取user-info，设置到UserContext
     */
    static class UserContextMethodInterceptor implements MethodInterceptor {
        @Override
        public Object invoke(MethodInvocation invocation) throws Throwable {
            for (Object arg : invocation.getArguments()) {
                if (arg instanceof Message) {
                    Message message = (Message) arg;
                    Long userId = message.getMessageProperties().getHeader("user-info");
                    if (userId != null) {
                        UserContext.setUser(userId);
                    }
                    break;
                }
            }
            try {
                return invocation.proceed();
            } finally {
                UserContext.removeUser();
            }
        }
    }
}
