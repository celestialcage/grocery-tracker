package com.shabet.grocery.stock.repository;

import com.querydsl.core.types.Predicate;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.shabet.grocery.food.domain.FoodCategory;
import com.shabet.grocery.food.domain.QFood;
import com.shabet.grocery.stock.domain.FoodStock;
import com.shabet.grocery.stock.domain.QFoodStock;
import com.shabet.grocery.stock.domain.StorageType;
import com.shabet.grocery.stock.dto.FoodStockSearchCondition;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class FoodStockSearchRepositoryImpl implements FoodStockSearchRepository {

    private final JPAQueryFactory queryFactory;

    @Override
    public List<FoodStock> search(Long householdId, FoodStockSearchCondition condition) {

        QFoodStock stock = QFoodStock.foodStock;
        QFood food = QFood.food;

        return queryFactory
                .selectFrom(stock)
                .join(stock.food, food).fetchJoin()
                .where(searchPredicates(householdId, condition, stock, food))
                .orderBy(stock.id.desc())
                .fetch();
    }

    @Override
    public Page<FoodStock> searchPage(
            Long householdId, FoodStockSearchCondition condition,
            Pageable pageable
    ) {
        QFoodStock stock = QFoodStock.foodStock;
        QFood food = QFood.food;

        // 검색 조건 구성
        List<FoodStock> content = queryFactory
                .selectFrom(stock)
                .join(stock.food, food).fetchJoin()
                .where(searchPredicates(householdId, condition, stock, food))
                .orderBy(stock.id.desc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        // 전체 개수 조회
        Long total = queryFactory
                .select(stock.count())
                .from(stock)
                .join(stock.food, food)
                .where(searchPredicates(householdId, condition, stock, food))
                .fetchOne();

        return new PageImpl<>(content, pageable, total == null ? 0L : total);
    }

    private BooleanExpression keywordContains(QFood food, QFoodStock stock, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }

        String value = keyword.trim();

        return food.name.containsIgnoreCase(value)
                .or(stock.productLabel.containsIgnoreCase(value));
    }

    private BooleanExpression categoryEq(QFood food, FoodCategory category) {
        return category == null
                ? null
                : food.category.eq(category);
    }

    private BooleanExpression storageTypeEq(
            QFoodStock stock, StorageType storageType
    ) {
        return storageType == null
                ? null
                : stock.storageType.eq(storageType);
    }

    private BooleanExpression openedEq(
            QFoodStock stock, Boolean opened
    ) {
        if (opened == null) {
            return null;
        }

        return opened
                ? stock.openedAt.isNotNull()
                : stock.openedAt.isNull();
    }

    private BooleanExpression depletedEq(
            QFoodStock stock, Boolean depleted
    ) {
        if (depleted == null) {
            return null;
        }

        return depleted
                ? stock.currentQuantity.eq(BigDecimal.ZERO)
                : stock.currentQuantity.gt(BigDecimal.ZERO);
    }

    // 기간(날짜) 조건, between 대신 분리. 시작일 또는 종료일 중 하나만 지정할 수 있도록 하기 위해서.
    // 날짜 조건이 지정되면 expiresAt이 null인 재고는 제외됨
    private BooleanExpression expiresFromGoe(
            QFoodStock stock,
            LocalDate from
    ) {
        return from == null
                ? null
                : stock.expiresAt.goe(from);
    }

    private BooleanExpression expiresToLoe(
            QFoodStock stock,
            LocalDate to
    ) {
        return to == null
                ? null
                : stock.expiresAt.loe(to);
    }

    private Predicate[] searchPredicates(
            Long householdId,
            FoodStockSearchCondition condition,
            QFoodStock stock,
            QFood food
    ) {
        return new Predicate[]{
                stock.household.id.eq(householdId),
                keywordContains(food, stock, condition.keyword()),
                categoryEq(food, condition.category()),
                storageTypeEq(stock, condition.storageType()),
                openedEq(stock, condition.opened()),
                depletedEq(stock, condition.depleted()),
                expiresFromGoe(stock, condition.expiresFrom()),
                expiresToLoe(stock, condition.expiresTo())
        };
    }
}
