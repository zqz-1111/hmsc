package com.hmall.search.listener;

import cn.hutool.json.JSONUtil;
import com.hmall.search.domain.po.ItemDoc;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.xcontent.XContentType;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ItemESListener {

    private final RestHighLevelClient client;

    private static final String INDEX_NAME = "items";

    /**
     * 监听商品新增/修改消息，写入ES
     */
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(name = "item.es.save.queue", durable = "true"),
            exchange = @Exchange(name = "item.direct"),
            key = "item.es.save"
    ))
    public void listenItemSave(String itemDocJson) {
        log.info("收到商品同步消息：{}", itemDocJson);
        try {
            ItemDoc itemDoc = JSONUtil.toBean(itemDocJson, ItemDoc.class);
            IndexRequest request = new IndexRequest(INDEX_NAME)
                    .id(itemDoc.getId())
                    .source(itemDocJson, XContentType.JSON);
            client.index(request, RequestOptions.DEFAULT);
            log.info("ES同步写入商品成功，id={}", itemDoc.getId());
        } catch (Exception e) {
            log.error("ES同步写入商品失败，json={}, 异常={}, 异常信息={}",
                    itemDocJson, e.getClass().getName(), e.getMessage(), e);
            throw new RuntimeException("ES同步写入失败", e);
        }
    }

    /**
     * 监听商品删除消息，从ES删除
     */
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(name = "item.es.delete.queue", durable = "true"),
            exchange = @Exchange(name = "item.direct"),
            key = "item.es.delete"
    ))
    public void listenItemDelete(Long itemId) {
        log.info("收到商品删除消息：id={}", itemId);
        try {
            DeleteRequest request = new DeleteRequest(INDEX_NAME, itemId.toString());
            client.delete(request, RequestOptions.DEFAULT);
            log.info("ES同步删除商品成功，id={}", itemId);
        } catch (Exception e) {
            log.error("ES同步删除商品失败，id={}", itemId, e);
            throw new RuntimeException("ES同步删除失败", e);
        }
    }
}
