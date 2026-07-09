package com.hmall.api.client.fallback;

import com.hmall.api.client.UserClient;
import com.hmall.common.exception.BizIllegalException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;

@Slf4j
public class UserClientFallback implements FallbackFactory<UserClient> {
    @Override
    public UserClient create(Throwable cause) {
        return new UserClient() {
            @Override
            public void deductMoney(String pw, Integer amount) {
                // 扣减余额是写操作，失败需要触发事务回滚
                log.error("远程调用UserClient#deductMoney方法出现异常，参数：pw={}, amount={}", pw, amount, cause);
                throw new BizIllegalException(cause);
            }
        };
    }
}