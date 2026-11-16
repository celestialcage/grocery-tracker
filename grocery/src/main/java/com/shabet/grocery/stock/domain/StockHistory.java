package com.shabet.grocery.stock.domain;

import com.shabet.grocery.global.entity.BaseTimeEntity;
import com.shabet.grocery.user.domain.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Entity
@Table(
        name = "stock_histories",
        indexes = {
                @Index(
                        name = "idx_stock_histories_stock_occurred_at",
                        columnList = "food_stock_id, occurred_at"
                ),
                @Index(
                        name = "idx_stock_histories_actor_user_id",
                        columnList = "actor_user_id"
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StockHistory extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "food_stock_id",
            nullable = false,
            foreignKey = @ForeignKey(
                    name = "fk_stock_histories_food_stock"
            )
    )
    private FoodStock foodStock;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "actor_user_id",
            nullable = false,
            foreignKey = @ForeignKey(
                    name = "fk_stock_histories_actor_user"
            )
    )
    private User actor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StockHistoryType type;

    @Column(
            name = "before_quantity",
            precision = 12,
            scale = 3
    )
    private BigDecimal beforeQuantity;

    @Column(
            name = "change_quantity",
            precision = 12,
            scale = 3
    )
    private BigDecimal changeQuantity;

    @Column(
            name = "after_quantity",
            precision = 12,
            scale = 3
    )
    private BigDecimal afterQuantity;

    @Enumerated(EnumType.STRING)
    @Column(name = "change_accuracy", length = 20)
    private QuantityAccuracy changeAccuracy;

    @Enumerated(EnumType.STRING)
    @Column(name = "after_accuracy", length = 20)
    private QuantityAccuracy afterAccuracy;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    // append-only 성격이라 변경 메서드들은 필요없음.

    private StockHistory(
            FoodStock foodStock,
            User actor,
            StockHistoryType type,
            BigDecimal beforeQuantity,
            BigDecimal changeQuantity,
            BigDecimal afterQuantity,
            QuantityAccuracy changeAccuracy,
            QuantityAccuracy afterAccuracy,
            LocalDateTime occurredAt
    ) {
        this.foodStock = foodStock;
        this.actor = actor;
        this.type = type;

        this.beforeQuantity = beforeQuantity;
        this.changeQuantity = changeQuantity;
        this.afterQuantity = afterQuantity;

        this.changeAccuracy = changeAccuracy;
        this.afterAccuracy = afterAccuracy;

        this.occurredAt = occurredAt;
    }

    // factory - registered / consumed / discarded / adjusted / opened
    public static StockHistory registered(
            FoodStock foodStock,
            User actor,
            LocalDateTime occurredAt
    ) {
        return new StockHistory(
                foodStock,
                actor,
                StockHistoryType.REGISTERED,

                null,
                foodStock.getCurrentQuantity(),
                foodStock.getCurrentQuantity(),

                foodStock.getQuantityAccuracy(),
                foodStock.getQuantityAccuracy(),

                occurredAt
        );
    }

    public static StockHistory consumed(
            FoodStock foodStock,
            User actor,
            BigDecimal beforeQuantity,
            BigDecimal consumedQuantity,
            QuantityAccuracy changeAccuracy,
            LocalDateTime occurredAt
    ) {
        return new StockHistory(
                foodStock,
                actor,
                StockHistoryType.CONSUMED,

                beforeQuantity,
                consumedQuantity.negate(),
                foodStock.getCurrentQuantity(),

                changeAccuracy,
                foodStock.getQuantityAccuracy(),

                occurredAt
        );
    }

    public static StockHistory discarded(
            FoodStock foodStock,
            User actor,
            BigDecimal beforeQuantity,
            BigDecimal discardedQuantity,
            QuantityAccuracy changeAccuracy,
            LocalDateTime occurredAt
    ) {
        return new StockHistory(
                foodStock,
                actor,
                StockHistoryType.DISCARDED,

                beforeQuantity,
                discardedQuantity.negate(),
                foodStock.getCurrentQuantity(),

                changeAccuracy,
                foodStock.getQuantityAccuracy(),

                occurredAt
        );
    }

    public static StockHistory adjusted(
            FoodStock foodStock,
            User actor,
            BigDecimal beforeQuantity,
            LocalDateTime occurredAt
    ) {
        BigDecimal afterQuantity = foodStock.getCurrentQuantity();

        BigDecimal changeQuantity = afterQuantity.subtract(beforeQuantity);

        return new StockHistory(
                foodStock,
                actor,
                StockHistoryType.ADJUSTED,

                beforeQuantity,
                changeQuantity,
                afterQuantity,

                foodStock.getQuantityAccuracy(),
                foodStock.getQuantityAccuracy(),

                occurredAt
        );
    }

    public static StockHistory opened(
            FoodStock foodStock,
            User actor,
            LocalDateTime occurredAt
    ) {
        return new StockHistory(
                foodStock,
                actor,
                StockHistoryType.OPENED,

                null,
                null,
                null,

                null,
                null,

                occurredAt
        );
    }
}
