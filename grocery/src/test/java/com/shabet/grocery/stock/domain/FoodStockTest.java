package com.shabet.grocery.stock.domain;

import com.shabet.grocery.food.domain.Food;
import com.shabet.grocery.food.domain.FoodCategory;
import com.shabet.grocery.food.domain.QuantityType;
import com.shabet.grocery.food.domain.StockUnit;
import com.shabet.grocery.household.domain.Household;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FoodStockTest {

    private final Household household = new Household("우리집");

    private final Food milk = Food.createSystem(
            "우유",
            FoodCategory.DAIRY,
            QuantityType.VOLUME,
            StockUnit.ML
    );

    private FoodStock createMilkStock(
            BigDecimal currentQuantity,
            QuantityAccuracy accuracy
    ) {
        return FoodStock.create(
                household,
                milk,
                new BigDecimal("1000"),
                currentQuantity,
                StockUnit.ML,
                accuracy,
                LocalDate.of(2026, 11, 16),
                LocalDate.of(2026, 11, 20),
                StorageType.REFRIGERATED,
                AcquisitionType.PURCHASED,
                null,
                null
        );
    }

    @Test
    @DisplayName("재고 생성 시 현재 수량은 0보다 커야 한다")
    void create_stock_with_zero_quantity_fails() {

        assertThatThrownBy(() ->
                createMilkStock(
                        BigDecimal.ZERO,
                        QuantityAccuracy.EXACT
                ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("등록 수량은 0보다 커야 합니다.")
        ;
    }

    @Test
    @DisplayName("정확한 재고에서 정확한 수량을 소비하면 잔량도 정확하다")
    void consume_exact_quantity_from_exact_stock() {
        FoodStock stock = createMilkStock(
                new BigDecimal("1000"),
                QuantityAccuracy.EXACT
        );

        Instant occurredAt = Instant.parse("2026-11-16T01:00:00Z");

        stock.consume(new BigDecimal("300"), QuantityAccuracy.EXACT, occurredAt);

        // 정확한 분량이 남아야
        assertThat(stock.getCurrentQuantity()).isEqualByComparingTo("700");

        // 현재 분량도 정확
        assertThat(stock.getQuantityAccuracy()).isEqualTo(QuantityAccuracy.EXACT);

        // 남은 게 확실하니 소진되지도 않음.
        assertThat(stock.getDepletedAt()).isNull();
        assertThat(stock.getDepletionReason()).isNull();
    }

    @Test
    @DisplayName("기존 잔량 또는 소비량이 추정값이면 변경 후 잔량도 추정값")
    void estimated_value_propagates_after_consumption() {
        FoodStock stock = createMilkStock(
                new BigDecimal("700"),
                QuantityAccuracy.ESTIMATED
        );

        stock.consume(
                new BigDecimal("100"),
                QuantityAccuracy.EXACT,
                Instant.parse("2026-11-16T01:00:00Z")
        );

        assertThat(stock.getCurrentQuantity()).isEqualByComparingTo("600");
        assertThat(stock.getQuantityAccuracy()).isEqualTo(QuantityAccuracy.ESTIMATED);
    }

    @Test
    @DisplayName("현재 재고보다 많은 양을 소비할 수는 없음")
    void cannot_consume_more_than_current_quantity() {
        FoodStock stock = createMilkStock(
                new BigDecimal("500"),
                QuantityAccuracy.EXACT
        );

        assertThatThrownBy(() ->
                stock.consume(
                        new BigDecimal("600"),
                        QuantityAccuracy.EXACT,
                        Instant.parse("2026-11-16T01:00:00Z")
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("현재 재고보다 많은 수량을 처리할 수 없습니다.");

        assertThat(stock.getCurrentQuantity()).isEqualByComparingTo("500");
    }

    @Test
    @DisplayName("재고를 전량 폐기하면 소진 시각과 폐기 사유가 기록됨")
    void discard_all_stock_marks_as_depleted() {
        FoodStock stock = createMilkStock(
                new BigDecimal("500"),
                QuantityAccuracy.EXACT
        );

        Instant occurredAt = Instant.parse("2026-11-16T02:00:00Z");

        stock.discard(
                new BigDecimal("500"),
                QuantityAccuracy.EXACT,
                occurredAt
        );

        assertThat(stock.getCurrentQuantity()).isEqualByComparingTo(BigDecimal.ZERO);

        assertThat(stock.getDepletedAt()).isEqualTo(occurredAt);

        assertThat(stock.getDepletionReason()).isEqualTo(DepletionReason.DISCARDED);
    }

    @Test
    @DisplayName("수량 보정으로 0이 되면 ADJUSTED 사유로 소진됨")
    void adjust_to_zero_marks_adjusted_depletion() {
        FoodStock stock = createMilkStock(
                new BigDecimal("500"),
                QuantityAccuracy.EXACT
        );

        Instant occurredAt = Instant.parse("2026-11-16T03:00:00Z");

        stock.adjust(
                BigDecimal.ZERO,
                QuantityAccuracy.EXACT,
                occurredAt
        );

        assertThat(stock.getCurrentQuantity()).isEqualByComparingTo(BigDecimal.ZERO);

        assertThat(stock.getDepletionReason()).isEqualTo(DepletionReason.ADJUSTED);

        assertThat(stock.getDepletedAt()).isEqualTo(occurredAt);
    }

    @Test
    @DisplayName("0이었던 재고를 다시 양수로 보정하면 소진 상태가 해제됨")
    void restore_depleted_stock_by_adjustment() {
        FoodStock stock = createMilkStock(
                new BigDecimal("500"),
                QuantityAccuracy.EXACT
        );

        stock.adjust(
                BigDecimal.ZERO,
                QuantityAccuracy.EXACT,
                Instant.parse("2026-11-16T03:00:00Z")
        );

        stock.adjust(
                new BigDecimal("200"),
                QuantityAccuracy.ESTIMATED,
                Instant.parse("2026-11-16T04:00:00Z")
        );

        assertThat(stock.getCurrentQuantity()).isEqualByComparingTo("200");

        assertThat(stock.getQuantityAccuracy()).isEqualTo(QuantityAccuracy.ESTIMATED);

        assertThat(stock.getDepletedAt()).isNull();
        assertThat(stock.getDepletionReason()).isNull();
    }

    @Test
    @DisplayName("이미 개봉된 재고는 다시 개봉 불가")
    void cannot_open_stock_twice() {
        FoodStock stock = createMilkStock(
                new BigDecimal("1000"),
                QuantityAccuracy.EXACT
        );

        Instant openAt = Instant.parse("2026-11-16T05:00:00Z");

        stock.open(openAt);

        assertThat(stock.getOpenedAt()).isEqualTo(openAt);

        assertThatThrownBy(() -> stock.open(Instant.parse("2026-11-16T06:00:00Z")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("이미 개봉된 재고입니다.");
    }

}