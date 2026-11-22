package com.shabet.grocery.stock.service;

import com.shabet.grocery.stock.domain.QuantityAccuracy;

import java.math.BigDecimal;

public record ConsumeFoodStockCommand(
        Long householdId,
        Long stockId,
        BigDecimal quantity,
        QuantityAccuracy operationAccuracy
) {}
