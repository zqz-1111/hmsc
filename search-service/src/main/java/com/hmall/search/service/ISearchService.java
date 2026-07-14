package com.hmall.search.service;

import com.hmall.common.domain.PageDTO;
import com.hmall.search.domain.dto.ItemDTO;
import com.hmall.search.domain.query.ItemPageQuery;

public interface ISearchService {

    /**
     * 搜索商品（ES 查询）
     */
    PageDTO<ItemDTO> search(ItemPageQuery query);
}