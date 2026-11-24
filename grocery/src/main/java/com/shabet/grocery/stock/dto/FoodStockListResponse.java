package com.shabet.grocery.stock.dto;

import com.shabet.grocery.food.domain.Food;
import com.shabet.grocery.food.domain.FoodCategory;
import com.shabet.grocery.food.domain.StockUnit;
import com.shabet.grocery.stock.domain.FoodStock;
import com.shabet.grocery.stock.domain.QuantityAccuracy;
import com.shabet.grocery.stock.domain.StorageType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record FoodStockListResponse(
        Long stockId,
        Long foodId,
        String foodName,
        FoodCategory foodCategory,

        BigDecimal initialQuantity,
        BigDecimal currentQuantity,
        StockUnit unit,
        QuantityAccuracy quantityAccuracy,

        LocalDate expiresAt,
        Instant openedAt,
        StorageType storageType,
        String productLabel,

        Instant depletedAt
) {

    public static FoodStockListResponse from(FoodStock stock) {
        Food food = stock.getFood();

        return new FoodStockListResponse(
                stock.getId(),
                food.getId(),
                food.getName(),
                food.getCategory(),

                stock.getInitialQuantity(),
                stock.getCurrentQuantity(),
                stock.getUnit(),
                stock.getQuantityAccuracy(),

                stock.getExpiresAt(),
                stock.getOpenedAt(),
                stock.getStorageType(),
                stock.getProductLabel(),

                stock.getDepletedAt()
        );
    }
}
