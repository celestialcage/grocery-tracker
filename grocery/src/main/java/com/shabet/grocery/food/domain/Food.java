package com.shabet.grocery.food.domain;

import com.shabet.grocery.global.entity.BaseTimeEntity;
import com.shabet.grocery.household.domain.Household;
import com.shabet.grocery.user.domain.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "foods",
        indexes = {
                @Index(
                        name = "idx_foods_household_id",
                        columnList = "household_id"
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Food extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "food_id")
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private FoodCategory category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private QuantityType quantityType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StockUnit defaultUnit;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private FoodSourceType sourceType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "household_id",
            foreignKey = @ForeignKey(name = "fk_foods_household")
    )
    private Household household;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "created_by_user_id",
            foreignKey = @ForeignKey(name = "fk_foods_created_by_user")
    )
    private User createdBy;

    @Column(nullable = false)
    private boolean active;

    private Food(
            String name,
            FoodCategory category,
            QuantityType quantityType,
            StockUnit defaultUnit,
            FoodSourceType sourceType,
            Household household,
            User createdBy
    ) {
        this.name = name;
        this.category = category;
        this.quantityType = quantityType;
        this.defaultUnit = defaultUnit;
        this.sourceType = sourceType;
        this.household = household;
        this.createdBy = createdBy;
        this.active = true;
    }

    // 논리적으로 가능한 food 종류만 생성하도록 만든 팩토리 메서드
    public static Food createSystem(
            String name,
            FoodCategory category,
            QuantityType quantityType,
            StockUnit defaultUnit
    ) {
        return new Food(
                name,
                category,
                quantityType,
                defaultUnit,
                FoodSourceType.SYSTEM,
                null,
                null
        );
    }

    public static Food createCustom(
            String name,
            FoodCategory category,
            QuantityType quantityType,
            StockUnit defaultUnit,
            Household household,
            User createdBy
    ) {
        return new Food(
                name,
                category,
                quantityType,
                defaultUnit,
                FoodSourceType.HOUSEHOLD_CUSTOM,
                household,
                createdBy
        );
    }

    // custom food 수정 메서드
    public void changeName(String name) {
        validateCustomFood();
        this.name = name;
    }

    public void changeCategory(FoodCategory category) {
        validateCustomFood();
        this.category = category;
    }

    public void changeQuantityPolicy(QuantityType quantityType, StockUnit defaultUnit) {
        validateCustomFood();

        this.quantityType = quantityType;
        this.defaultUnit = defaultUnit;
    }

    public void deactivate() {
        validateCustomFood();
        this.active = false;
    }

    private void validateCustomFood() {
        if (sourceType != FoodSourceType.HOUSEHOLD_CUSTOM) {
            throw new IllegalStateException("시스템 식재료는 수정할 수 없습니다.");
            // 나중에 예외 정책 잡을 때 DomainException, FoodNotEditableException 같은 거 고민
        }
    }

    // enum QuantityType, Unit 검증은 추후 데이터 생기면 상세히 추가
}
