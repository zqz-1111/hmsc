package com.hmall.search.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.hmall.common.domain.PageDTO;
import com.hmall.search.domain.dto.ItemDTO;
import com.hmall.search.domain.query.ItemPageQuery;
import com.hmall.search.service.ISearchService;
import lombok.RequiredArgsConstructor;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SearchServiceImpl implements ISearchService {

    private final RestHighLevelClient client;

    private static final String INDEX_NAME = "items";

    @Override
    public PageDTO<ItemDTO> search(ItemPageQuery query) {
        try {
            // 1. 构建搜索请求
            SearchRequest request = new SearchRequest(INDEX_NAME);

            // 从 query 取值（兼容 Lombok 未生效的情况，通过反射取字段）
            String key = query.getKey();
            String brand = query.getBrand();
            String category = query.getCategory();
            Integer minPrice = query.getMinPrice();
            Integer maxPrice = query.getMaxPrice();
            int pageNo = query.getPageNo();
            int pageSize = query.getPageSize();

            // 2. 构建查询条件（bool query）
            request.source()
                    .query(QueryBuilders.boolQuery()
                            // 关键字模糊搜索（name 字段，ik_smart 分词）
                            .must(StrUtil.isNotBlank(key)
                                    ? QueryBuilders.matchQuery("name", key)
                                    : QueryBuilders.matchAllQuery())
                            // 品牌精确过滤
                            .filter(StrUtil.isNotBlank(brand)
                                    ? QueryBuilders.termQuery("brand", brand)
                                    : QueryBuilders.matchAllQuery())
                            // 分类精确过滤
                            .filter(StrUtil.isNotBlank(category)
                                    ? QueryBuilders.termQuery("category", category)
                                    : QueryBuilders.matchAllQuery())
                            // 价格范围过滤
                            .filter(minPrice != null || maxPrice != null
                                    ? QueryBuilders.rangeQuery("price")
                                        .gte(minPrice)
                                        .lte(maxPrice)
                                    : QueryBuilders.matchAllQuery())
                    )
                    // 按更新时间降序
                    .sort("updateTime", SortOrder.DESC)
                    // 分页
                    .from((pageNo - 1) * pageSize)
                    .size(pageSize);

            // 3. 发送请求
            SearchResponse response = client.search(request, RequestOptions.DEFAULT);

            // 4. 解析结果
            long total = response.getHits().getTotalHits().value;
            long pages = (total + pageSize - 1) / pageSize;

            List<ItemDTO> list = new ArrayList<>();
            for (SearchHit hit : response.getHits().getHits()) {
                Map<String, Object> source = hit.getSourceAsMap();
                ItemDTO item = BeanUtil.mapToBean(source, ItemDTO.class, true);
                list.add(item);
            }

            return new PageDTO<>(total, pages, list);
        } catch (IOException e) {
            throw new RuntimeException("ES 查询失败", e);
        }
    }
}
