package com.shabet.grocery.stock.dto;

import com.shabet.grocery.food.domain.Food;
import com.shabet.grocery.food.domain.FoodCategory;
import com.shabet.grocery.food.domain.StockUnit;
import com.shabet.grocery.stock.domain.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record FoodStockDetailResponse(
        Long stockId,
        Long foodId,
        String foodName,
        FoodCategory foodCategory,

        BigDecimal initialQuantity,
        BigDecimal currentQuantity,
        StockUnit unit,
        QuantityAccuracy quantityAccuracy,

        LocalDate purchasedAt,
        LocalDate expiresAt,
        Instant openedAt,

        StorageType storageType,
        AcquisitionType acquisitionType,
        String productLabel,
        String memo,

        Instant depletedAt,
        DepletionReason depletionReason,
        Long version
) {
    // from(FoodStock stock) 구현
    public static FoodStockDetailResponse from(FoodStock stock) {
        Food food = stock.getFood();

        return new FoodStockDetailResponse(
                stock.getId(),
                food.getId(),
                food.getName(),
                food.getCategory(),

                stock.getInitialQuantity(),
                stock.getCurrentQuantity(),
                stock.getUnit(),
                stock.getQuantityAccuracy(),

                stock.getPurchasedAt(),
                stock.getExpiresAt(),
                stock.getOpenedAt(),

                stock.getStorageType(),
                stock.getAcquisitionType(),
                stock.getProductLabel(),
                stock.getMemo(),

                stock.getDepletedAt(),
                stock.getDepletionReason(),
                stock.getVersion()
        );
    }
}
