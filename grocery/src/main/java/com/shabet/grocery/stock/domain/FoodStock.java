package com.shabet.grocery.stock.domain;

import com.shabet.grocery.food.domain.Food;
import com.shabet.grocery.food.domain.StockUnit;
import com.shabet.grocery.global.entity.BaseTimeEntity;
import com.shabet.grocery.household.domain.Household;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

@Getter
@Entity
@Table(
        name = "food_stocks",
        indexes = {
                @Index(
                        name = "idx_food_stocks_food_id",
                        columnList = "food_id"
                ),
                @Index(
                        name = "idx_food_stocks_household_expires_at",
                        columnList = "household_id, expires_at"
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FoodStock extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "food_stock_id")
    private Long id;

    // 동시성 처리용 필드. 향후 OptimisticLockException 처리까지 해보면 JPA 동시성 제어 경험 포인트
    @Version
    private Long version;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "household_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_food_stocks_household")
    )
    private Household household;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "food_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_food_stocks_food")
    )
    private Food food;

    @Column(
            name = "initial_quantity",
            nullable = false,
            precision = 12,
            scale = 3
    )
    private BigDecimal initialQuantity;


    @Column(
            name = "current_quantity",
            nullable = false,
            precision = 12,
            scale = 3
    )
    private BigDecimal currentQuantity;

    // 거의 불변으로 해야 history가 안정적
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StockUnit unit;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private QuantityAccuracy quantityAccuracy;

    private LocalDate purchasedAt;

    private LocalDate expiresAt;

    private Instant openedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private StorageType storageType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AcquisitionType acquisitionType;

    @Column(length = 100)
    private String productLabel;

    @Column(length = 500)
    private String memo;

    private Instant depletedAt;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private DepletionReason depletionReason;


    private FoodStock(
            Household household,
            Food food,
            BigDecimal initialQuantity,
            BigDecimal currentQuantity,
            StockUnit unit,
            QuantityAccuracy quantityAccuracy,
            LocalDate purchasedAt,
            LocalDate expiresAt,
            StorageType storageType,
            AcquisitionType acquisitionType,
            String productLabel,
            String memo
    ) {
        validateCreateQuantities(initialQuantity, currentQuantity);

        this.household = household;
        this.food = food;

        this.initialQuantity = initialQuantity;
        this.currentQuantity = currentQuantity;

        this.unit = unit;
        this.quantityAccuracy = quantityAccuracy;

        this.purchasedAt = purchasedAt;
        this.expiresAt = expiresAt;

        this.storageType = storageType;
        this.acquisitionType = acquisitionType;

        this.productLabel = productLabel;
        this.memo = memo;
    }

    // 팩토리 메서드
    public static FoodStock create(
            Household household,
            Food food,
            BigDecimal initialQuantity,
            BigDecimal currentQuantity,
            StockUnit unit,
            QuantityAccuracy quantityAccuracy,
            LocalDate purchasedAt,
            LocalDate expiresAt,
            StorageType storageType,
            AcquisitionType acquisitionType,
            String productLabel,
            String memo
    ) {
        Objects.requireNonNull(unit, "단위는 필수입니다.");
        Objects.requireNonNull(quantityAccuracy, "정확도는 필수입니다.");
        Objects.requireNonNull(storageType, "보관방법은 필수입니다.");
        Objects.requireNonNull(acquisitionType, "취득방식은 필수입니다.");
        Objects.requireNonNull(household, "가구는 필수입니다.");
        Objects.requireNonNull(food, "식품 마스터는 필수입니다.");

        return new FoodStock(
                household,
                food,
                initialQuantity,
                currentQuantity,
                unit,
                quantityAccuracy,
                purchasedAt,
                expiresAt,
                storageType,
                acquisitionType,
                productLabel,
                memo
        );
    }

    // 검증 헬퍼
    private void validateCreateQuantities(BigDecimal initialQuantity, BigDecimal currentQuantity) {
        if (initialQuantity == null ||
                initialQuantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("기준 수량은 0보다 커야 합니다.");
        }

        if (currentQuantity == null ||
                currentQuantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("등록 수량은 0보다 커야 합니다.");
        }

        // Adjusted 상태 생각해서 빼기
//        if (currentQuantity.compareTo(initialQuantity) > 0) {
//            throw new IllegalArgumentException("현재 수량은 기준 수량을 초과할 수 없습니다.");
//        }
    }


    // 수량은 consume, discard, adjust 셋 중 하나의 행위로서만 변경 ----------
    // 소비
    public void consume(BigDecimal quantity, QuantityAccuracy operationAccuracy,
                        Instant occurredAt) {
        Objects.requireNonNull(operationAccuracy, "정확도는 필수입니다.");
        Objects.requireNonNull(occurredAt, "발생시간은 필수입니다.");

        validateDecreaseQuantity(quantity);

        currentQuantity = currentQuantity.subtract(quantity);

        quantityAccuracy = resolveAccuracy(quantityAccuracy, operationAccuracy);

        updateDepletionState(DepletionReason.CONSUMED, occurredAt);
    }

    // 버림
    public void discard(
            BigDecimal quantity,
            QuantityAccuracy operationAccuracy,
            Instant occurredAt
    ) {
        Objects.requireNonNull(operationAccuracy, "정확도는 필수입니다.");
        Objects.requireNonNull(occurredAt, "발생시간은 필수입니다.");

        validateDecreaseQuantity(quantity);

        currentQuantity = currentQuantity.subtract(quantity);

        quantityAccuracy = resolveAccuracy(quantityAccuracy, operationAccuracy);

        updateDepletionState(DepletionReason.DISCARDED, occurredAt);
    }

    // 수량 보정
    public void adjust(
            BigDecimal newQuantity,
            QuantityAccuracy newAccuracy,
            Instant occurredAt
    ) {
        Objects.requireNonNull(newAccuracy, "정확도는 필수입니다.");
        Objects.requireNonNull(occurredAt, "발생시간은 필수입니다.");

        if (newQuantity == null ||
                newQuantity.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("재고 수량은 0 이상이어야 합니다.");
        }

        if (newAccuracy == null) {
            throw new IllegalArgumentException("정확도는 비워둘 수 없습니다.");
        }

        currentQuantity = newQuantity;
        quantityAccuracy = newAccuracy;

        updateDepletionState(DepletionReason.ADJUSTED, occurredAt);
    }

    // 개봉 처리
    public void open(Instant openedAt) {
        Objects.requireNonNull(openedAt, "개봉 시각은 필수입니다.");

        if (this.openedAt != null) {
            throw new IllegalStateException("이미 개봉된 재고입니다.");
        }

        this.openedAt = openedAt;
    }
    // 미개봉으로 되돌리는 건 사용자 실수 수정 기능이 정말 필요해질 때 별도 설계


    // 기본 정보 수정
    public void changeExpirationDate(LocalDate expiresAt) {
        this.expiresAt = expiresAt;
    }

    public void changePurchasedDate(LocalDate purchasedAt) {
        this.purchasedAt = purchasedAt;
    }

    public void changeStorageType(StorageType storageType) {
        this.storageType = storageType;
    }

    public void changeProductLabel(String productLabel) {
        this.productLabel = productLabel;
    }

    public void changeMemo(String memo) {
        this.memo = memo;
    }


    // 프라이빗 헬퍼 -------------------------------------------------------------
    private void validateDecreaseQuantity(BigDecimal quantity) {
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("처리 수량은 0보다 커야 합니다.");
        }

        if (quantity.compareTo(currentQuantity) > 0) {
            throw new IllegalArgumentException("현재 재고보다 많은 수량을 처리할 수 없습니다.");
        }
    }

    // 당연한 정확도 규칙...
    private QuantityAccuracy resolveAccuracy(
            QuantityAccuracy currentAccuracy, QuantityAccuracy operationAccuracy
    ) {
        if (currentAccuracy == QuantityAccuracy.EXACT &&
                operationAccuracy == QuantityAccuracy.EXACT) {
            return QuantityAccuracy.EXACT;
        }

        return QuantityAccuracy.ESTIMATED;
    }

    // 소진 처리
    private void updateDepletionState(
            DepletionReason reason, Instant occurredAt
    ) {
        if (currentQuantity.compareTo(BigDecimal.ZERO) == 0) {
            depletedAt = occurredAt;
            depletionReason = reason;
            return;
        }

        depletedAt = null;
        depletionReason = null;
    }
}
