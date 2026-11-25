package com.shabet.grocery.stock.dto;

import com.shabet.grocery.food.domain.FoodCategory;
import com.shabet.grocery.stock.domain.StorageType;

public record FoodStockSearchCondition(
        String keyword,
        FoodCategory category,
        StorageType storageType
) {}
