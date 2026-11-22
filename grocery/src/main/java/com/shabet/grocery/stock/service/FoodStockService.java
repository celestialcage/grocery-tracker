package com.shabet.grocery.stock.service;

import com.shabet.grocery.food.domain.Food;
import com.shabet.grocery.food.domain.FoodSourceType;
import com.shabet.grocery.food.repository.FoodRepository;
import com.shabet.grocery.household.domain.Household;
import com.shabet.grocery.household.repository.HouseholdRepository;
import com.shabet.grocery.household.service.HouseholdAccessValidator;
import com.shabet.grocery.stock.domain.FoodStock;
import com.shabet.grocery.stock.domain.QuantityAccuracy;
import com.shabet.grocery.stock.domain.StockHistory;
import com.shabet.grocery.stock.repository.FoodStockRepository;
import com.shabet.grocery.stock.repository.StockHistoryRepository;
import com.shabet.grocery.user.domain.User;
import com.shabet.grocery.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class FoodStockService {

    private final UserRepository userRepository;
    private final HouseholdRepository householdRepository;
    private final HouseholdAccessValidator accessValidator;
    private final FoodRepository foodRepository;
    private final FoodStockRepository foodStockRepository;
    private final StockHistoryRepository stockHistoryRepository;

    // FoodStock과 StockHistory는 동일한 트랜잭션 안에서 저장
    @Transactional
    public Long register(Long actorUserId, RegisterFoodStockCommand command) {

        User actor = findActor(actorUserId);

        Household household = householdRepository.findById(command.householdId())
                .orElseThrow(() -> new NoSuchElementException("가구가 없습니다."));

        accessValidator.requireMember(command.householdId(), actorUserId);

        Food food = foodRepository.findById(command.foodId())
                .orElseThrow(() -> new NoSuchElementException("식품이 없습니다."));

        validateAvailableFood(food, household);

        FoodStock stock = FoodStock.create(
                household,
                food,
                command.initialQuantity(),
                command.currentQuantity(),
                command.unit(),
                command.quantityAccuracy(),
                command.purchasedAt(),
                command.expiresAt(),
                command.storageType(),
                command.acquisitionType(),
                command.productLabel(),
                command.memo()
        );

        FoodStock savedStock = foodStockRepository.save(stock);

        StockHistory history = StockHistory.registered(
                savedStock,
                actor,
                Instant.now()
        );

        stockHistoryRepository.save(history);

        return savedStock.getId();
    }

    @Transactional
    public void consume(Long actorUserId, ConsumeFoodStockCommand command) {

        // 1. 작업자 확인
        User actor = findActor(actorUserId);

        // 2. household 구성원 확인
        // 3. 해당 household의 재고 조회
        FoodStock stock = findAccessibleStock(actorUserId, command.householdId(), command.stockId());

        // 4. 변경 이전 수량 저장
        BigDecimal beforeQuantity = stock.getCurrentQuantity();

        Instant occurredAt = Instant.now();

        // 5. 도메인 행위 실행
        stock.consume(
                command.quantity(),
                command.operationAccuracy(),
                occurredAt
        );

        // 6. 변경 이력 생성
        StockHistory history = StockHistory.consumed(
                stock,
                actor,
                beforeQuantity,
                command.quantity(),
                command.operationAccuracy(),
                occurredAt
        );

        // 7. 이력 저장
        stockHistoryRepository.save(history);

        // FoodStockRepository.save(stock)은 호출 안함. Dirty Checking
    }

    @Transactional
    public void discard(
            Long actorUserId,
            DiscardFoodStockCommand command
    )  {
        // todo: consume과 공통 부분은 향후 추출 여부 판단
        User actor = findActor(actorUserId);

        FoodStock stock = findAccessibleStock(actorUserId, command.householdId(), command.stockId());

        BigDecimal beforeQuantity = stock.getCurrentQuantity();
        Instant occurredAt = Instant.now();

        stock.discard(
                command.quantity(),
                command.operationAccuracy(),
                occurredAt
        );

        StockHistory history = StockHistory.discarded(
                stock,
                actor,
                beforeQuantity,
                command.quantity(),
                command.operationAccuracy(),
                occurredAt
        );

        stockHistoryRepository.save(history);
    }

    @Transactional
    public void adjust(
            Long actorUserId,
            AdjustFoodStockCommand command
    ) {
        User actor = findActor(actorUserId);

        FoodStock stock = findAccessibleStock(actorUserId, command.householdId(), command.stockId());

        // 보정 전 상태를 반드시 보관 (이건.. 의존관계 없는 구조상 그렇지)
        BigDecimal beforeQuantity = stock.getCurrentQuantity();
        QuantityAccuracy beforeAccuracy = stock.getQuantityAccuracy();

        Instant occurredAt = Instant.now();

        // stock adjust
        stock.adjust(
                command.newQuantity(),
                command.newAccuracy(),
                occurredAt
        );

        // adjust 기록 insert
        StockHistory history = StockHistory.adjusted(
                stock,
                actor,
                beforeQuantity,
                beforeAccuracy,
                occurredAt
        );

        stockHistoryRepository.save(history);
    }

    @Transactional
    public void open(
            Long actorUserId,
            OpenFoodStockCommand command
    ) {
        User actor = findActor(actorUserId);

        FoodStock stock = findAccessibleStock(actorUserId, command.householdId(), command.stockId());

        Instant openedAt = Instant.now();

        // 기존 도메인 메서드로 개봉 처리
        stock.open(openedAt);

        // 개봉 이력 생성
        StockHistory history = StockHistory.opened(
                stock,
                actor,
                openedAt
        );

        stockHistoryRepository.save(history);
    }

    private void validateAvailableFood(Food food, Household household) {

        if (!food.isActive()) {
            throw new IllegalArgumentException("비활성화된 식품입니다.");
        }

        if (food.getSourceType() == FoodSourceType.HOUSEHOLD_CUSTOM) {
            if (food.getHousehold() == null ||
                    !Objects.equals(food.getHousehold().getId(), household.getId())) {
                throw new SecurityException("다른 가구의 사용자 식품은 등록할 수 없습니다.");
            }
        }
    }

    private User findActor(Long actorUserId) {
        return userRepository.findById(actorUserId)
                .orElseThrow(()->
                        new NoSuchElementException("사용자가 없습니다.")
                );
    }

    private FoodStock findAccessibleStock(
            Long actorUserId,
            Long householdId,
            Long stockId
    ) {
        accessValidator.requireMember(
                householdId, actorUserId
        );

        return foodStockRepository
                .findByIdAndHousehold_Id(stockId, householdId)
                .orElseThrow(() -> new NoSuchElementException("재고가 없습니다."));
    }
}
