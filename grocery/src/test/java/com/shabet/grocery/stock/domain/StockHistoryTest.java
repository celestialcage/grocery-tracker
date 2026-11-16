package com.shabet.grocery.stock.domain;


import com.shabet.grocery.food.domain.Food;
import com.shabet.grocery.food.domain.FoodCategory;
import com.shabet.grocery.food.domain.QuantityType;
import com.shabet.grocery.food.domain.StockUnit;
import com.shabet.grocery.household.domain.Household;
import com.shabet.grocery.user.domain.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class StockHistoryTest {

    private final Household household = new Household("우리집");

    private final User actor = new User(
            "test@example.com",
            "encoded-password",
            "tester"
    );

    private final Food milk = Food.createSystem(
            "우유",
            FoodCategory.DAIRY,
            QuantityType.VOLUME,
            StockUnit.ML
    );

    private FoodStock createStock(
            BigDecimal quantity,
            QuantityAccuracy accuracy
    ) {
        return FoodStock.create(
                household,
                milk,
                new BigDecimal("1000"),
                quantity,
                StockUnit.ML,
                accuracy,
                null,
                null,
                StorageType.REFRIGERATED,
                AcquisitionType.EXISTING,
                null,
                null
        );
    }

    @Test
    @DisplayName("재고 등록 History는 현재 등록 수량을 기록함")
    void registered_history() {
        FoodStock stock = createStock(
                new BigDecimal("500"),
                QuantityAccuracy.ESTIMATED
        );

        Instant occurredAt =
                Instant.parse("2026-11-16T01:00:00Z");

        StockHistory history = StockHistory.registered(
                stock,
                actor,
                occurredAt
        );

        assertThat(history.getType()).isEqualTo(StockHistoryType.REGISTERED);

        assertThat(history.getBeforeQuantity()).isNull();

        assertThat(history.getChangeQuantity()).isEqualByComparingTo("500");

        assertThat(history.getAfterQuantity()).isEqualByComparingTo("500");

        assertThat(history.getChangeAccuracy()).isEqualTo(QuantityAccuracy.ESTIMATED);

        assertThat(history.getAfterAccuracy()).isEqualTo(QuantityAccuracy.ESTIMATED);
    }

    @Test
    @DisplayName("소비 History는 변경 전, 변화량, 변경 후 수량을 기록함")
    void consumed_history() {
        FoodStock stock = createStock(
                new BigDecimal("1000"),
                QuantityAccuracy.EXACT
        );

        Instant occurredAt =
                Instant.parse("2026-11-16T02:00:00Z");

        BigDecimal before = stock.getCurrentQuantity();

        BigDecimal consumed = new BigDecimal("300");

        stock.consume(
                consumed,
                QuantityAccuracy.EXACT,
                occurredAt
        );

        StockHistory history = StockHistory.consumed(
                stock,
                actor,
                before,
                consumed,
                QuantityAccuracy.EXACT,
                occurredAt
        );

        assertThat(history.getBeforeQuantity()).isEqualByComparingTo("1000");

        assertThat(history.getChangeQuantity()).isEqualByComparingTo("-300");

        assertThat(history.getAfterQuantity()).isEqualByComparingTo("700");

        assertThat(history.getChangeAccuracy()).isEqualTo(QuantityAccuracy.EXACT);

        assertThat(history.getAfterAccuracy()).isEqualTo(QuantityAccuracy.EXACT);
    }

    @Test
    @DisplayName("추정 잔량에서 정확한 잔량으로 보정해도 변화량은 추정값")
    void adjusted_history_accuracy() {
        FoodStock stock = createStock(
                new BigDecimal("700"),
                QuantityAccuracy.ESTIMATED
        );

        Instant occurredAt =
                Instant.parse("2026-11-16T03:00:00Z");

        BigDecimal beforeQuantity =
                stock.getCurrentQuantity();

        QuantityAccuracy beforeAccuracy =
                stock.getQuantityAccuracy();

        stock.adjust(
                new BigDecimal("500"),
                QuantityAccuracy.EXACT,
                occurredAt
        );

        StockHistory history = StockHistory.adjusted(
                stock,
                actor,
                beforeQuantity,
                beforeAccuracy,
                occurredAt
        );

        assertThat(history.getBeforeQuantity()).isEqualByComparingTo("700");

        assertThat(history.getChangeQuantity()).isEqualByComparingTo("-200");

        assertThat(history.getAfterQuantity()).isEqualByComparingTo("500");

        assertThat(history.getChangeAccuracy()).isEqualTo(QuantityAccuracy.ESTIMATED);

        assertThat(history.getAfterAccuracy()).isEqualTo(QuantityAccuracy.EXACT);
    }

    @Test
    @DisplayName("개봉 history에는 수량 변화 없음")
    void opened_history_has_no_quantity_change() {
        FoodStock stock = createStock(
                new BigDecimal("1000"),
                QuantityAccuracy.EXACT
        );

        Instant occurredAt =
                Instant.parse("2026-11-16T04:00:00Z");

        stock.open(occurredAt);

        StockHistory history = StockHistory.opened(
                stock,
                actor,
                occurredAt
        );

        assertThat(history.getType()).isEqualTo(StockHistoryType.OPENED);

        assertThat(history.getBeforeQuantity()).isNull();
        assertThat(history.getChangeQuantity()).isNull();
        assertThat(history.getAfterQuantity()).isNull();

        assertThat(history.getChangeAccuracy()).isNull();
        assertThat(history.getAfterAccuracy()).isNull();
    }
}