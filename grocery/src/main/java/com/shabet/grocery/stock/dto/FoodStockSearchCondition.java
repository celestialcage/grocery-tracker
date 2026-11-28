package com.shabet.grocery.stock.dto;

import com.shabet.grocery.food.domain.FoodCategory;
import com.shabet.grocery.stock.domain.StorageType;

import java.time.LocalDate;

public record FoodStockSearchCondition(
        String keyword,
        FoodCategory category,
        StorageType storageType,
        Boolean opened,
        Boolean depleted,
        LocalDate expiresFrom,
        LocalDate expiresTo
) {
    // record의 Compact Constructor - 전체 필드를 받는 기본 생성자에 검증 로직을 추가
    public FoodStockSearchCondition {
        if (expiresFrom != null
                && expiresTo != null
                && expiresFrom.isAfter(expiresTo)) {
            throw new IllegalArgumentException(
                    "소비기한 시작일은 종료일보다 늦을 수 없습니다."
            );
        }
    }

    // 기존 인자 3개 사용하는 테스트와 호환
    public FoodStockSearchCondition(
            String keyword,
            FoodCategory category,
            StorageType storageType
    ) {
        this(keyword, category, storageType,
                null, null, null, null);
    }

    // 기존 인자 5개 사용하는 테스트와 호환
    public FoodStockSearchCondition(
            String keyword,
            FoodCategory category,
            StorageType storageType,
            Boolean opened,
            Boolean depleted
    ) {
        this(keyword, category, storageType,
                opened, depleted, null, null);
    }
}
