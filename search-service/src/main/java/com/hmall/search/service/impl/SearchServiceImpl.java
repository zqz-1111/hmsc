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
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.functionscore.FunctionScoreQueryBuilder;
import org.elasticsearch.index.query.functionscore.ScoreFunctionBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.filter.Filter;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
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

            String key = query.getKey();
            String sortBy = query.getSortBy();
            Boolean isAsc = query.getIsAsc();
            int pageNo = query.getPageNo();
            int pageSize = query.getPageSize();

            // 2. 构建查询条件（bool query）
            request.source().query(buildQuery(query));

            // 3. 排序：支持动态排序字段，默认按 updateTime 降序
            if (StrUtil.isNotBlank(sortBy)) {
                SortOrder order = Boolean.TRUE.equals(isAsc) ? SortOrder.ASC : SortOrder.DESC;
                request.source().sort(sortBy, order);
            } else {
                // 默认按相关性评分排序，function_score 的竞价排名才生效
                request.source().sort("_score", SortOrder.DESC);
            }

            // 4. 分页
            request.source()
                    .from((pageNo - 1) * pageSize)
                    .size(pageSize);

            // 5. 发送请求
            SearchResponse response = client.search(request, RequestOptions.DEFAULT);

            // 6. 解析结果
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

    @Override
    public Map<String, List<String>> filters(ItemPageQuery query) {
        try {
            SearchRequest request = new SearchRequest(INDEX_NAME);

            // 构建不含 category 过滤的查询（聚合分类用）
            BoolQueryBuilder categoryQuery = buildQueryWithout(query, "category");

            // 构建不含 brand 过滤的查询（聚合品牌用）
            BoolQueryBuilder brandQuery = buildQueryWithout(query, "brand");

            // 顶层 query 不影响 filter 聚合，各自带独立完整条件
            request.source()
                    .query(QueryBuilders.matchAllQuery())
                    .aggregation(AggregationBuilders.filter("category_agg", categoryQuery)
                            .subAggregation(AggregationBuilders.terms("category").field("category").size(50)))
                    .aggregation(AggregationBuilders.filter("brand_agg", brandQuery)
                            .subAggregation(AggregationBuilders.terms("brand").field("brand").size(50)))
                    .size(0); // 不需要搜索结果，只要聚合

            SearchResponse response = client.search(request, RequestOptions.DEFAULT);

            // 解析聚合结果
            Map<String, List<String>> result = new HashMap<>();

            Aggregations aggregations = response.getAggregations();

            // 解析分类聚合：category_agg(Filter) → category(Terms)
            Filter categoryFilter = aggregations.get("category_agg");
            Terms categoryTerms = categoryFilter.getAggregations().get("category");
            List<String> categories = new ArrayList<>();
            for (Terms.Bucket bucket : categoryTerms.getBuckets()) {
                categories.add(bucket.getKeyAsString());
            }
            result.put("category", categories);

            // 解析品牌聚合：brand_agg(Filter) → brand(Terms)
            Filter brandFilter = aggregations.get("brand_agg");
            Terms brandTerms = brandFilter.getAggregations().get("brand");
            List<String> brands = new ArrayList<>();
            for (Terms.Bucket bucket : brandTerms.getBuckets()) {
                brands.add(bucket.getKeyAsString());
            }
            result.put("brand", brands);

            return result;
        } catch (IOException e) {
            throw new RuntimeException("ES 聚合查询失败", e);
        }
    }

    /**
     * 构建查询条件（带竞价排名：isAD=true 的商品算分 boost）
     */
    private QueryBuilder buildQuery(ItemPageQuery query) {
        String key = query.getKey();
        String brand = query.getBrand();
        String category = query.getCategory();
        Integer minPrice = query.getMinPrice();
        Integer maxPrice = query.getMaxPrice();

        BoolQueryBuilder boolQuery = QueryBuilders.boolQuery()
                .must(StrUtil.isNotBlank(key)
                        ? QueryBuilders.matchQuery("name", key)
                        : QueryBuilders.matchAllQuery())
                .filter(QueryBuilders.termQuery("status", 1));

        if (StrUtil.isNotBlank(brand)) {
            boolQuery.filter(QueryBuilders.termQuery("brand", brand));
        }
        if (StrUtil.isNotBlank(category)) {
            boolQuery.filter(QueryBuilders.termQuery("category", category));
        }
        if (minPrice != null || maxPrice != null) {
            boolQuery.filter(QueryBuilders.rangeQuery("price").gte(minPrice).lte(maxPrice));
        }

        // function_score：isAD=true 的商品权重提升，排在前面
        return QueryBuilders.functionScoreQuery(
                boolQuery,
                new FunctionScoreQueryBuilder.FilterFunctionBuilder[]{
                        new FunctionScoreQueryBuilder.FilterFunctionBuilder(
                                QueryBuilders.termQuery("isAD", true),
                                ScoreFunctionBuilders.weightFactorFunction(10)
                        )
                }
        ).boostMode(org.elasticsearch.common.lucene.search.function.CombineFunction.MULTIPLY);
    }

    /**
     * 构建查询条件，排除指定字段的过滤（用于聚合）
     */
    private BoolQueryBuilder buildQueryWithout(ItemPageQuery query, String excludeField) {
        String key = query.getKey();
        String brand = query.getBrand();
        String category = query.getCategory();
        Integer minPrice = query.getMinPrice();
        Integer maxPrice = query.getMaxPrice();

        BoolQueryBuilder boolQuery = QueryBuilders.boolQuery()
                .must(StrUtil.isNotBlank(key)
                        ? QueryBuilders.matchQuery("name", key)
                        : QueryBuilders.matchAllQuery())
                .filter(QueryBuilders.termQuery("status", 1));

        // 只添加非排除字段的过滤条件
        if (!"brand".equals(excludeField) && StrUtil.isNotBlank(brand)) {
            boolQuery.filter(QueryBuilders.termQuery("brand", brand));
        }
        if (!"category".equals(excludeField) && StrUtil.isNotBlank(category)) {
            boolQuery.filter(QueryBuilders.termQuery("category", category));
        }
        if (minPrice != null || maxPrice != null) {
            boolQuery.filter(QueryBuilders.rangeQuery("price").gte(minPrice).lte(maxPrice));
        }

        return boolQuery;
    }
}
