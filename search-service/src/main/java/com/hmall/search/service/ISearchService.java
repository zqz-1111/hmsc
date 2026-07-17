package com.hmall.search.service;

import com.hmall.common.domain.PageDTO;
import com.hmall.search.domain.dto.ItemDTO;
import com.hmall.search.domain.query.ItemPageQuery;

import java.util.List;
import java.util.Map;

public interface ISearchService {

    /**
     * 搜索商品（ES 查询）
     */
    PageDTO<ItemDTO> search(ItemPageQuery query);

    /**
     * 搜索过滤项聚合（分类、品牌）
     */
    Map<String, List<String>> filters(ItemPageQuery query);
}