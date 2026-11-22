package com.shabet.grocery.stock.service;


import com.shabet.grocery.config.TestcontainersConfiguration;
import com.shabet.grocery.food.domain.Food;
import com.shabet.grocery.food.domain.FoodCategory;
import com.shabet.grocery.food.domain.QuantityType;
import com.shabet.grocery.food.domain.StockUnit;
import com.shabet.grocery.food.repository.FoodRepository;
import com.shabet.grocery.household.domain.Household;
import com.shabet.grocery.household.domain.HouseholdMember;
import com.shabet.grocery.household.domain.HouseholdRole;
import com.shabet.grocery.household.repository.HouseholdMemberRepository;
import com.shabet.grocery.household.repository.HouseholdRepository;
import com.shabet.grocery.stock.domain.*;
import com.shabet.grocery.stock.repository.FoodStockRepository;
import com.shabet.grocery.stock.repository.StockHistoryRepository;
import com.shabet.grocery.user.domain.User;
import com.shabet.grocery.user.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Slf4j
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class FoodStockServiceIntegrationTest {

    @Autowired FoodStockService foodStockService;
    @Autowired UserRepository userRepository;
    @Autowired HouseholdRepository householdRepository;
    @Autowired HouseholdMemberRepository memberRepository;
    @Autowired FoodRepository foodRepository;
    @Autowired FoodStockRepository stockRepository;
    @Autowired StockHistoryRepository historyRepository;

    private User actor;
    private Household household;
    private Food milk;

    @BeforeEach
    void setUp() {
        // FoodStock은 독립적으로 존재할 수 없고 Household와 Food 참조.
        // 등록한 사용자가 Household 구성원인지도 확인함.

        actor = createUser();

        household = householdRepository.save(
                new Household("우리집")
        );

        memberRepository.save(
                new HouseholdMember(
                        actor,
                        household,
                        HouseholdRole.OWNER,
                        Instant.now()
                )
        );

        milk = foodRepository.save(
                Food.createSystem(
                        "우유",
                        FoodCategory.DAIRY,
                        QuantityType.VOLUME,
                        StockUnit.ML
                )
        );
    }

    @Test
    @DisplayName("foodStockService로 register하면 stock과 history가 쌓임")
    void register_save_stock_and_history() {

        // when
        Long stockId = foodStockService.register(
                actor.getId(),
                command(household.getId(), milk.getId())
        );

        // then: 재고 확인
        FoodStock stock = stockRepository.findById(stockId).orElseThrow();

        assertThat(stock.getInitialQuantity()).isEqualByComparingTo("1000");

        assertThat(stock.getCurrentQuantity()).isEqualByComparingTo("650");

        assertThat(stock.getQuantityAccuracy()).isEqualTo(QuantityAccuracy.ESTIMATED);

        assertThat(stock.getCreatedAt()).isNotNull();

        // then: 등록 이력 확인
        List<StockHistory> histories = historyRepository.findAllByFoodStock_Id(stockId);

        assertThat(histories).hasSize(1);

        StockHistory history = histories.getFirst();

        assertThat(history.getType())
                .isEqualTo(StockHistoryType.REGISTERED);

        assertThat(history.getBeforeQuantity()).isNull();

        assertThat(history.getChangeQuantity()).isEqualByComparingTo("650");

        assertThat(history.getAfterQuantity()).isEqualByComparingTo("650");

        assertThat(history.getChangeAccuracy()).isEqualTo(QuantityAccuracy.ESTIMATED);

        assertThat(history.getAfterAccuracy()).isEqualTo(QuantityAccuracy.ESTIMATED);

        assertThat(history.getFoodStock().getId()).isEqualTo(stockId);

        assertThat(history.getActor().getId()).isEqualTo(actor.getId());

        assertThat(history.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("비회원은 stock register 안 됨. 시큐리티 예외")
    void non_member_cannot_register_stock() {

        User outsider = createUser();

        assertThatThrownBy(()->
                foodStockService.register(
                        outsider.getId(),
                        command(household.getId(), milk.getId())
                )
        ).isInstanceOf(SecurityException.class);

        assertThat(stockRepository.countByHousehold_Id(household.getId()))
                .isZero();
    }

    @Test
    @DisplayName("다른 집의 커스텀 식품 마스터 register 안 됨. 시큐리티 예외")
    void cannot_register_other_household_custom_food() {

        User otherUser = createUser();

        Household otherHousehold = householdRepository.save(
                new Household("다른 집")
        );

        memberRepository.save(
                new HouseholdMember(
                        otherUser,
                        otherHousehold,
                        HouseholdRole.OWNER,
                        Instant.now()
                )
        );

        Food customFood = foodRepository.save(
                Food.createCustom(
                        "특제 우유",
                        FoodCategory.DAIRY,
                        QuantityType.VOLUME,
                        StockUnit.ML,
                        otherHousehold,
                        otherUser
                )
        );

        assertThatThrownBy(() ->
                        foodStockService.register(
                actor.getId(),
                command(household.getId(), customFood.getId())
            )
        ).isInstanceOf(SecurityException.class);

        assertThat(stockRepository.countByHousehold_Id(household.getId()))
                .isZero();
    }

    @Test
    @DisplayName("비활성 식품은 등록 불가")
    void cannot_register_inactive_food() {

        Food customFood = Food.createCustom(
                "비활성 우유",
                FoodCategory.DAIRY,
                QuantityType.VOLUME,
                StockUnit.ML,
                household,
                actor
        );

        customFood.deactivate();
        foodRepository.save(customFood);

        assertThatThrownBy(() ->
                foodStockService.register(
                        actor.getId(),
                        command(household.getId(), customFood.getId())
                )
        ).isInstanceOf(IllegalArgumentException.class);

        assertThat(stockRepository.countByHousehold_Id(household.getId()))
                .isZero();
    }

    // FoodStockService consume
    @Test
    @DisplayName("일부 소비와 history 검증")
    void consume_updates_stock_and_creates_history() {
        // given: 약 650ml 남은 우유 등록
        Long stockId = foodStockService.register(
                actor.getId(),
                command(household.getId(), milk.getId())
        );

        Long beforeVersion = stockRepository
                .findById(stockId).orElseThrow()
                .getVersion();

        // when: 정확히 100ml 소비
        foodStockService.consume(
                actor.getId(),
                new ConsumeFoodStockCommand(
                        household.getId(),
                        stockId,
                        new BigDecimal("100"),
                        QuantityAccuracy.EXACT
                )
        );

        // then: 실제 DB에서 재조회
        FoodStock stock = stockRepository
                .findById(stockId)
                .orElseThrow();

        assertThat(stock.getCurrentQuantity())
                .isEqualByComparingTo("550");

        // 기존 수량이 추정값이니 여전히 ESTIMATED
        assertThat(stock.getQuantityAccuracy())
                .isEqualTo(QuantityAccuracy.ESTIMATED);

        assertThat(stock.getDepletedAt()).isNull();

        // @Version 증가 확인
        assertThat(stock.getVersion())
                .isEqualTo(beforeVersion + 1);

        // History 확인
        List<StockHistory> histories =
                historyRepository.findAllByFoodStock_Id(stockId);

        // REGISTERED + CONSUMED
        assertThat(histories).hasSize(2);

        StockHistory consumed = histories.stream()
                .filter(h->h.getType()==StockHistoryType.CONSUMED)
                .findFirst()
                .orElseThrow();

        assertThat(consumed.getBeforeQuantity())
                .isEqualByComparingTo("650");

        assertThat(consumed.getChangeQuantity())
                .isEqualByComparingTo("-100");

        assertThat(consumed.getAfterQuantity())
                .isEqualByComparingTo("550");

        assertThat(consumed.getChangeAccuracy())
                .isEqualTo(QuantityAccuracy.EXACT);

        assertThat(consumed.getAfterAccuracy())
                .isEqualTo(QuantityAccuracy.ESTIMATED);
    }

    @Test
    @DisplayName("전량 소비")
    void consume_all_marks_stock_as_depleted() {
        Long stockId = foodStockService.register(
                actor.getId(),
                command(household.getId(), milk.getId())
        );

        foodStockService.consume(
                actor.getId(),
                new ConsumeFoodStockCommand(
                        household.getId(),
                        stockId,
                        new BigDecimal("650"),
                        QuantityAccuracy.EXACT
                )
        );

        FoodStock stock = stockRepository.findById(stockId)
                .orElseThrow();

        assertThat(stock.getCurrentQuantity())
                .isEqualByComparingTo("0");

        assertThat(stock.getDepletionReason())
                .isEqualTo(DepletionReason.CONSUMED);

        assertThat(stock.getDepletedAt()).isNotNull();

        // 등록, 소진으로 딱 2개
        assertThat(historyRepository.findAllByFoodStock_Id(stockId))
                .hasSize(2);
    }

    @Test
    @DisplayName("재고 초과 소비 거부")
    void cannot_consume_more_than_remaining_stock() {
        Long stockId = foodStockService.register(
                actor.getId(),
                command(household.getId(), milk.getId())
        );

        assertThatThrownBy(() ->
                        foodStockService.consume(
                        actor.getId(),
                        new ConsumeFoodStockCommand(
                                household.getId(),
                                stockId,
                                new BigDecimal("700"),
                                QuantityAccuracy.EXACT
                        )
                )
        ).isInstanceOf(IllegalArgumentException.class);

        // 기존 수량 유지
        FoodStock stock = stockRepository.findById(stockId)
                .orElseThrow();

        assertThat(stock.getCurrentQuantity())
                .isEqualByComparingTo("650");

        // 실패한 CONSUMED 히스토리는 생성되지 않음. 등록만 1개
        assertThat(historyRepository.findAllByFoodStock_Id(stockId))
                .hasSize(1);
    }

    // 프라이빗 헬퍼
    private User createUser() {
        return userRepository.save(
                new User(
                        "test-" + UUID.randomUUID() + "@example.com",
                        "test-password",
                        "tester"
                )
        );
    }

    private RegisterFoodStockCommand command(
            Long householdId, Long foodId
    ) {
        return new RegisterFoodStockCommand(
                householdId,
                foodId,
                new BigDecimal("1000"),
                new BigDecimal("650"),
                StockUnit.ML,
                QuantityAccuracy.ESTIMATED,
                LocalDate.of(2026, 11, 16),
                LocalDate.of(2026, 11, 20),
                StorageType.REFRIGERATED,
                AcquisitionType.EXISTING,
                "매일우유",
                "이미 일부 사용한 우유"
        );
    }

}