package com.shabet.grocery.stock.repository;

import com.shabet.grocery.stock.domain.FoodStock;
import com.shabet.grocery.stock.dto.FoodStockSearchCondition;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface FoodStockSearchRepository {
    List<FoodStock> search(Long householdId, FoodStockSearchCondition condition);

    // 일단 페이징 별도 검증
    Page<FoodStock> searchPage(
            Long householdId, FoodStockSearchCondition condition, Pageable pageable
    );
}
