package com.shabet.grocery.stock.repository;

import com.shabet.grocery.stock.domain.FoodStock;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FoodStockRepository extends JpaRepository<FoodStock, Long>, FoodStockSearchRepository {

    // 테스트 - 재고 등록 실패 시 해당 household에 재고가 생성되지 않았는지 검증하기 위해 추가한 메서드
    long countByHousehold_Id(Long householdId);

    // 해당하는 Household에 속한 재고민 조회. (단순 findById에 household 조건 추가)
    Optional<FoodStock> findByIdAndHousehold_Id(Long stockId, Long householdId);

    // 목록 조회 메서드 - 최근 등록 재고부터 표시하기 위한 id 내림차순 (소진된 재고 포함. 추후 검색 기능에서 필터)
    @EntityGraph(attributePaths = "food") // jpql 대신 derived query로 fetch join 같이 하는 거
    List<FoodStock> findAllByHousehold_IdOrderByIdDesc(Long householdId);

    // 참고: jpql 방식
//    @Query("""
//    SELECT fs
//    FROM FoodStock fs
//    JOIN FETCH fs.food
//    WHERE fs.household.id = :householdId
//    ORDER BY fs.id DESC
//    """)
//    List<FoodStock> findAllWithFood(
//            @Param("householdId") Long householdId
//    );
}
