package com.shabet.grocery.stock.repository;

import com.shabet.grocery.stock.domain.StockHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StockHistoryRepository extends JpaRepository<StockHistory, Long> {

    // 테스트를 위해 특정 재고에 연결된 history만 조회하도록 메서드 추가
    List<StockHistory> findAllByFoodStock_Id(Long foodStockId);
}
