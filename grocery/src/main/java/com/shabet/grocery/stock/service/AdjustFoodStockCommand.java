package com.shabet.grocery.stock.service;

import com.shabet.grocery.stock.domain.QuantityAccuracy;

import java.math.BigDecimal;

public record AdjustFoodStockCommand(
        Long householdId,
        Long stockId,
        BigDecimal newQuantity,
        QuantityAccuracy newAccuracy
) {}
