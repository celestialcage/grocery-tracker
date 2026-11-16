package com.shabet.grocery.food.repository;

import com.shabet.grocery.food.domain.Food;
import com.shabet.grocery.food.domain.FoodSourceType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FoodRepository extends JpaRepository<Food, Long> {

    List<Food> findAllBySourceTypeAndActiveTrue(FoodSourceType sourceType);

    List<Food> findAllBySourceTypeAndHousehold_IdAndActiveTrue(
            FoodSourceType sourceType,
            Long householdId
    );
}
