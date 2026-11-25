package com.shabet.grocery.stock.repository;

import com.shabet.grocery.stock.domain.FoodStock;

import java.util.List;

public interface FoodStockSearchRepository {
    List<FoodStock> search(Long householdId, String keyword);
}
