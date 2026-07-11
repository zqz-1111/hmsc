package com.hmall.api.mq;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 下单消息：用于通知购物车服务清理指定商品
 */
@Data
public class OrderMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 下单的商品ID集合
     */
    private List<Long> itemIds;
}
