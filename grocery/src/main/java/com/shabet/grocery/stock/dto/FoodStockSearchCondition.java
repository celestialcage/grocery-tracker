package com.shabet.grocery.stock.dto;

import com.shabet.grocery.food.domain.FoodCategory;
import com.shabet.grocery.stock.domain.StorageType;

public record FoodStockSearchCondition(
        String keyword,
        FoodCategory category,
        StorageType storageType,
        Boolean opened,
        Boolean depleted
) {
    // 기존 인자 3개 사용하는 테스트와 호환
    public FoodStockSearchCondition(
            String keyword,
            FoodCategory category,
            StorageType storageType
    ) {
        this(keyword, category, storageType, null, null);
    }
}
