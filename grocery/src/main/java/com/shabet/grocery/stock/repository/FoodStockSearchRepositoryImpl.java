package com.shabet.grocery.stock.repository;

import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.shabet.grocery.food.domain.QFood;
import com.shabet.grocery.stock.domain.FoodStock;
import com.shabet.grocery.stock.domain.QFoodStock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class FoodStockSearchRepositoryImpl implements FoodStockSearchRepository {

    private final JPAQueryFactory queryFactory;

    @Override
    public List<FoodStock> search(Long householdId, String keyword) {

        QFoodStock stock = QFoodStock.foodStock;
        QFood food = QFood.food;

        return queryFactory
                .selectFrom(stock)
                .join(stock.food, food).fetchJoin()
                .where(
                        stock.household.id.eq(householdId),
                        keywordContains(food, stock, keyword)
                )
                .orderBy(stock.id.desc())
                .fetch();
    }

    private BooleanExpression keywordContains(QFood food, QFoodStock stock, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }

        String value = keyword.trim();

        return food.name.containsIgnoreCase(value)
                .or(stock.productLabel.containsIgnoreCase(value));
    }
}
