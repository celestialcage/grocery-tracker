package com.shabet.grocery.stock.repository;

import com.shabet.grocery.stock.domain.FoodStock;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FoodStockRepository extends JpaRepository<FoodStock, Long> {
}
