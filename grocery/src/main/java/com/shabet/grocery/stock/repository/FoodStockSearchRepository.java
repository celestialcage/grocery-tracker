package com.shabet.grocery.stock.repository;

import com.shabet.grocery.stock.domain.FoodStock;
import com.shabet.grocery.stock.dto.FoodStockSearchCondition;

import java.util.List;

public interface FoodStockSearchRepository {
    List<FoodStock> search(Long householdId, FoodStockSearchCondition condition);
}
