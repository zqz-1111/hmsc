package com.hmall.cart.listener;

import com.hmall.api.mq.OrderMessage;
import com.hmall.cart.domain.po.Cart;
import com.hmall.cart.service.ICartService;
import com.hmall.common.utils.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ClearCartListener {

    private final ICartService cartService;

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(name = "cart.clear.queue", durable = "true"),
            exchange = @Exchange(name = "trade.topic", type = "topic"),
            key = "order.create"
    ))
    public void listenClearCart(OrderMessage message) {
        // UserContext由MqConfig自动设置，直接用即可
        cartService.lambdaUpdate()
                .eq(Cart::getUserId, UserContext.getUser())
                .in(Cart::getItemId, message.getItemIds())
                .remove();
    }
}