package com.shabet.grocery.stock.repository;

import com.shabet.grocery.stock.domain.FoodStock;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FoodStockRepository extends JpaRepository<FoodStock, Long> {

    // 테스트 - 재고 등록 실패 시 해당 household에 재고가 생성되지 않았는지 검증하기 위해 추가한 메서드
    long countByHousehold_Id(Long householdId);

    // 해당하는 Household에 속한 재고민 조회. (단순 findById에 household 조건 추가)
    Optional<FoodStock> findByIdAndHousehold_Id(Long stockId, Long householdId);
}
