package com.shabet.grocery.stock.repository;

import com.shabet.grocery.stock.domain.StockHistory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockHistoryRepository extends JpaRepository<StockHistory, Long> {
}
