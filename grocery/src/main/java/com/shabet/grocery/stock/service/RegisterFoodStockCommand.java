package com.shabet.grocery.stock.service;

import com.shabet.grocery.food.domain.StockUnit;
import com.shabet.grocery.stock.domain.AcquisitionType;
import com.shabet.grocery.stock.domain.QuantityAccuracy;
import com.shabet.grocery.stock.domain.StorageType;

import java.math.BigDecimal;
import java.time.LocalDate;

// Controller의 HTTP 요청 DTO와 구분
public record RegisterFoodStockCommand(
        Long householdId,
        Long foodId,
        BigDecimal initialQuantity,
        BigDecimal currentQuantity,
        StockUnit unit,
        QuantityAccuracy quantityAccuracy,
        LocalDate purchasedAt,
        LocalDate expiresAt,
        StorageType storageType,
        AcquisitionType acquisitionType,
        String productLabel,
        String memo
) {}
