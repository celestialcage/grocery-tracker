package com.shabet.grocery.food.service;

import com.shabet.grocery.food.domain.*;
import com.shabet.grocery.food.repository.FoodRepository;
import com.shabet.grocery.household.domain.Household;
import com.shabet.grocery.household.repository.HouseholdRepository;
import com.shabet.grocery.household.service.HouseholdAccessValidator;
import com.shabet.grocery.user.domain.User;
import com.shabet.grocery.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
public class FoodService {

    private final FoodRepository foodRepository;
    private final UserRepository userRepository;
    private final HouseholdRepository householdRepository;
    private final HouseholdAccessValidator accessValidator;

    // todo: 이후 dto 반환으로 변경할 것
    @Transactional(readOnly = true)
    public List<Food> findAvailableFoods(Long userId, Long householdId) {

        accessValidator.requireMember(householdId, userId);

        List<Food> foods = new ArrayList<>();

        foods.addAll(foodRepository.findAllBySourceTypeAndActiveTrue(
                FoodSourceType.SYSTEM
        ));

        foods.addAll(foodRepository.findAllBySourceTypeAndHousehold_IdAndActiveTrue(
                FoodSourceType.HOUSEHOLD_CUSTOM,
                householdId
        ));

        return foods;
    }

    @Transactional
    public Long createCustomFood(
            Long userId,
            Long householdId,
            String name,
            FoodCategory category,
            QuantityType quantityType,
            StockUnit defaultUnit
    ) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("사용자가 없습니다."));

        Household household = householdRepository.findById(householdId)
                .orElseThrow(() -> new NoSuchElementException("가구가 없습니다."));

        accessValidator.requireMember(householdId, userId);

        Food food = Food.createCustom(
                name,
                category,
                quantityType,
                defaultUnit,
                household,
                user
        );

        return foodRepository.save(food).getId();
    }
}
