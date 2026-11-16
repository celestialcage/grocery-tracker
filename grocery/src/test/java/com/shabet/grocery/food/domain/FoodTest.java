package com.shabet.grocery.food.domain;

import com.shabet.grocery.household.domain.Household;
import com.shabet.grocery.user.domain.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FoodTest {

    @Test
    @DisplayName("시스템 food는 수정할 수 없음")
    void system_food_cannot_be_modified() {
        Food food = Food.createSystem(
                "우유",
                FoodCategory.DAIRY,
                QuantityType.VOLUME,
                StockUnit.ML
        );

        assertThatThrownBy(() ->
                food.changeName("다른 우유")
        )
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("시스템 식재료는 수정할 수 없습니다.");

    }

    @Test
    @DisplayName("custom food 생성 시 household는 필수")
    void custom_food_requires_household() {
        User user = new User(
                "test@example.com",
                "password",
                "tester"
        );

        assertThatThrownBy(() ->
                Food.createCustom(
                        "특제 장아찌",
                        FoodCategory.ETC,
                        QuantityType.PORTION,
                        StockUnit.PACK,
                        null,
                        user
                )
        ).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("custom food는 비활성화할 수 있다")
    void custom_food_can_be_deactivated() {
        Household household = new Household("우리집");

        User user = new User("suer@example.com", "password", "tester");

        Food food = Food.createCustom(
                "특제 장아찌",
                FoodCategory.ETC,
                QuantityType.PORTION,
                StockUnit.PACK,
                household,
                user
        );

        food.deactivate();

        assertThat(food.isActive()).isFalse();

    }
}