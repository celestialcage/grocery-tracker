package com.shabet.grocery.stock.service;

public record OpenFoodStockCommand(
        Long householdId,
        Long stockId
) {}
