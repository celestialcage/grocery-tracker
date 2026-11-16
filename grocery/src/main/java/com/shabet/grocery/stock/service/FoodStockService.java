package com.shabet.grocery.stock.service;

import com.shabet.grocery.food.domain.Food;
import com.shabet.grocery.food.domain.FoodSourceType;
import com.shabet.grocery.food.repository.FoodRepository;
import com.shabet.grocery.household.domain.Household;
import com.shabet.grocery.household.repository.HouseholdRepository;
import com.shabet.grocery.household.service.HouseholdAccessValidator;
import com.shabet.grocery.stock.domain.FoodStock;
import com.shabet.grocery.stock.domain.StockHistory;
import com.shabet.grocery.stock.repository.FoodStockRepository;
import com.shabet.grocery.stock.repository.StockHistoryRepository;
import com.shabet.grocery.user.domain.User;
import com.shabet.grocery.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

        User actor = userRepository.findById(actorUserId)
                .orElseThrow(() -> new NoSuchElementException("사용자가 없습니다."));

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
}
