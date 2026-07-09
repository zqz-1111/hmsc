package com.hmall.api.client.fallback;

import com.hmall.api.client.TradeClient;
import com.hmall.common.exception.BizIllegalException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;

@Slf4j
public class TradeClientFallback implements FallbackFactory<TradeClient> {
    @Override
    public TradeClient create(Throwable cause) {
        return new TradeClient() {
            @Override
            public void markOrderPaySuccess(Long orderId) {
                // 标记订单支付成功是写操作，失败需要触发事务回滚
                log.error("远程调用TradeClient#markOrderPaySuccess方法出现异常，参数：{}", orderId, cause);
                throw new BizIllegalException(cause);
            }
        };
    }
}
