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

        assertThat(stock.getCurrentQuantity()).isEqualByComparingTo("550");

        // 기존 수량이 추정값이니 여전히 ESTIMATED
        assertThat(stock.getQuantityAccuracy()).isEqualTo(QuantityAccuracy.ESTIMATED);

        assertThat(stock.getDepletedAt()).isNull();

        // @Version 증가 확인
        assertThat(stock.getVersion()).isEqualTo(beforeVersion + 1);

        // History 확인
        List<StockHistory> histories =
                historyRepository.findAllByFoodStock_Id(stockId);

        // REGISTERED + CONSUMED
        assertThat(histories).hasSize(2);

        StockHistory consumed = histories.stream()
                .filter(h->h.getType()==StockHistoryType.CONSUMED)
                .findFirst()
                .orElseThrow();

        assertThat(consumed.getBeforeQuantity()).isEqualByComparingTo("650");

        assertThat(consumed.getChangeQuantity()).isEqualByComparingTo("-100");

        assertThat(consumed.getAfterQuantity()).isEqualByComparingTo("550");

        assertThat(consumed.getChangeAccuracy()).isEqualTo(QuantityAccuracy.EXACT);

        assertThat(consumed.getAfterAccuracy()).isEqualTo(QuantityAccuracy.ESTIMATED);
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

        FoodStock stock = stockRepository.findById(stockId).orElseThrow();

        assertThat(stock.getCurrentQuantity()).isEqualByComparingTo("0");

        assertThat(stock.getDepletionReason()).isEqualTo(DepletionReason.CONSUMED);

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

    @Test
    @DisplayName("일부 폐기 시 재고와 DISCARDED history 변경")
    void discard_partial_stock() {
        Long stockId = foodStockService.register(
                actor.getId(),
                command(household.getId(), milk.getId())
        );

        foodStockService.discard(
                actor.getId(),
                new DiscardFoodStockCommand(
                        household.getId(),
                        stockId,
                        new BigDecimal("250"),
                        QuantityAccuracy.EXACT
                )
        );

        FoodStock stock = stockRepository.findById(stockId).orElseThrow();

        assertThat(stock.getCurrentQuantity()).isEqualByComparingTo("400");

        assertThat(stock.getQuantityAccuracy()).isEqualTo(QuantityAccuracy.ESTIMATED);

        assertThat(stock.getDepletedAt()).isNull();

        List<StockHistory> histories =
                historyRepository.findAllByFoodStock_Id(stockId);

        assertThat(histories).hasSize(2);

        StockHistory discarded = histories.stream()
                .filter(h->h.getType()==StockHistoryType.DISCARDED)
                .findFirst()
                .orElseThrow();

        assertThat(discarded.getBeforeQuantity()).isEqualByComparingTo("650");

        assertThat(discarded.getChangeQuantity()).isEqualByComparingTo("-250");

        assertThat(discarded.getAfterQuantity()).isEqualByComparingTo("400");

        assertThat(discarded.getChangeAccuracy()).isEqualTo(QuantityAccuracy.EXACT);

        assertThat(discarded.getAfterAccuracy()).isEqualTo(QuantityAccuracy.ESTIMATED);

    }

    @Test
    @DisplayName("전량 폐기 시 DISCARDED 소진 상태")
    void discard_all_stock() {
        Long stockId = foodStockService.register(
                actor.getId(),
                command(household.getId(), milk.getId())
        );

        foodStockService.discard(
                actor.getId(),
                new DiscardFoodStockCommand(
                        household.getId(),
                        stockId,
                        new BigDecimal("650"),
                        QuantityAccuracy.EXACT
                )
        );

        FoodStock stock = stockRepository.findById(stockId).orElseThrow();

        assertThat(stock.getCurrentQuantity()).isEqualByComparingTo("0");

        assertThat(stock.getDepletionReason()).isEqualTo(DepletionReason.DISCARDED);

        assertThat(stock.getDepletedAt()).isNotNull();
    }

    // 추정 수량을 정확한 수량으로 보정
    @Test
    @DisplayName("보정 전후 정확도가 다른 경우 history 검증")
    void adjust_stock_to_exact_quantity() {
        Long stockId = foodStockService.register(
                actor.getId(),
                command(household.getId(), milk.getId())
        );

        foodStockService.adjust(
                actor.getId(),
                new AdjustFoodStockCommand(
                        household.getId(),
                        stockId,
                        new BigDecimal("400"),
                        QuantityAccuracy.EXACT
                )
        );

        FoodStock stock = stockRepository.findById(stockId).orElseThrow();

        assertThat(stock.getCurrentQuantity()).isEqualByComparingTo("400");

        assertThat(stock.getQuantityAccuracy()).isEqualTo(QuantityAccuracy.EXACT);

        StockHistory adjusted =
                historyRepository.findAllByFoodStock_Id(stockId)
                        .stream()
                        .filter(h->h.getType()==StockHistoryType.ADJUSTED)
                        .findFirst()
                        .orElseThrow();

        assertThat(adjusted.getBeforeQuantity()).isEqualByComparingTo("650");

        assertThat(adjusted.getChangeQuantity()).isEqualByComparingTo("-250");

        assertThat(adjusted.getAfterQuantity()).isEqualByComparingTo("400");

        assertThat(adjusted.getChangeAccuracy()).isEqualTo(QuantityAccuracy.ESTIMATED);

        assertThat(adjusted.getAfterAccuracy()).isEqualTo(QuantityAccuracy.EXACT);
    }

    @Test
    @DisplayName("0으로 보정한 재고를 다시 양수로 복구")
    void adjust_zero_then_restore() {
        Long stockId = foodStockService.register(
                actor.getId(),
                command(household.getId(), milk.getId())
        );

        // 650 → 0
        foodStockService.adjust(
                actor.getId(),
                new AdjustFoodStockCommand(
                        household.getId(),
                        stockId,
                        BigDecimal.ZERO,
                        QuantityAccuracy.EXACT
                )
        );

        FoodStock depleted = stockRepository.findById(stockId).orElseThrow();

        assertThat(depleted.getDepletionReason()).isEqualTo(DepletionReason.ADJUSTED);

        assertThat(depleted.getDepletedAt()).isNotNull();

        // 0 → 300
        foodStockService.adjust(
                actor.getId(),
                new AdjustFoodStockCommand(
                        household.getId(),
                        stockId,
                        new BigDecimal("300"),
                        QuantityAccuracy.EXACT
                )
        );

        FoodStock restored = stockRepository.findById(stockId).orElseThrow();

        assertThat(restored.getCurrentQuantity()).isEqualByComparingTo("300");

        assertThat(restored.getDepletedAt()).isNull();
        assertThat(restored.getDepletionReason()).isNull();

        // REGISTERED + ADJUSTED + ADJUSTED
        assertThat(historyRepository.findAllByFoodStock_Id(stockId)).hasSize(3);
    }

    // todo: 경계 조건 테스트 2개 추가 (FoodStock 엔티티 단위 테스트에서 이미 검증하긴 함)
    // Service 통합 테스트에서는 예외 발생 시 DB에 변경이 남지 않는지까지 확인 가능.
    // 1. 잔량 650ml에서 700ml 폐기 시도
    // => IllegalArgumentException, 수량 유지, History 추가 없음
    // 2. adjust(-1) 호출
    // => IllegalArgumentException, 수량 유지, History 추가 없음
    // 참고)
    // 또 권한 검증은 consume(), discard(), adjust()가 동일한 접근 제어 패턴을 사용하므로,
    // 최소한 다른 가구의 재고를 변경할 수 없는지도 통합 테스트로 하나씩 확인하면 좋다.

    // open 테스트
    // todo: open 약간 고민... 열고 소비 안하는 게 좀 이상하지만 백엔드에서 분리할 이유가 있을지... 해봐야 알듯
    @Test
    @DisplayName("재고 개봉 시 openedAt과 OPENED history 기록")
    void open_stock_creates_history() {
        Long stockId = foodStockService.register(
                actor.getId(),
                command(household.getId(), milk.getId())
        );

        FoodStock before = stockRepository.findById(stockId).orElseThrow();

        Long beforeVersion = before.getVersion();

        // when
        foodStockService.open(
                actor.getId(),
                new OpenFoodStockCommand(
                        household.getId(),
                        stockId
                )
        );

        // then
        FoodStock stock = stockRepository.findById(stockId).orElseThrow();

        assertThat(stock.getOpenedAt()).isNotNull();

        // 개봉해도 수량과 정확도는 그대로
        assertThat(stock.getCurrentQuantity()).isEqualByComparingTo("650");

        assertThat(stock.getQuantityAccuracy()).isEqualTo(QuantityAccuracy.ESTIMATED);

        // openedAt 변경이 dirty checking으로 반영됨.
        assertThat(stock.getVersion()).isEqualTo(beforeVersion + 1);
        List<StockHistory> histories =
                historyRepository.findAllByFoodStock_Id(stockId);

        assertThat(histories).hasSize(2);

        StockHistory opened = histories.stream()
                .filter(h -> h.getType() == StockHistoryType.OPENED)
                .findFirst()
                .orElseThrow();

        assertThat(opened.getOccurredAt()).isEqualTo(stock.getOpenedAt());

        // 수량 변경이 없는 사건
        assertThat(opened.getBeforeQuantity()).isNull();
        assertThat(opened.getChangeQuantity()).isNull();
        assertThat(opened.getAfterQuantity()).isNull();

        assertThat(opened.getChangeAccuracy()).isNull();
        assertThat(opened.getAfterAccuracy()).isNull();

        assertThat(opened.getActor().getId()).isEqualTo(actor.getId());
    }

    @Test
    @DisplayName("이미 개봉된 재고는 다시 개봉할 수 없음")
    void cannot_open_stock_twice() {
        Long stockId = foodStockService.register(
                actor.getId(),
                command(household.getId(), milk.getId())
        );

        OpenFoodStockCommand openCommand =
                new OpenFoodStockCommand(
                        household.getId(),
                        stockId
                );

        // 첫 번째 개봉
        foodStockService.open(actor.getId(), openCommand);

        FoodStock stock = stockRepository.findById(stockId).orElseThrow();

        Instant firstOpenedAt = stock.getOpenedAt();

        // 두 번째 개봉은 실패
        assertThatThrownBy(() ->
                foodStockService.open(actor.getId(), openCommand)
        )
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("이미 개봉된 재고입니다.");

        FoodStock after = stockRepository.findById(stockId).orElseThrow();

        // 최초 개봉 시각 유지
        assertThat(after.getOpenedAt()).isEqualTo(firstOpenedAt);

        // REGISTERED + OPENED
        // 실패한 개봉으로 History가 추가되지 않음
        assertThat(historyRepository.findAllByFoodStock_Id(stockId)).hasSize(2);
    }

    @Test
    @DisplayName("가구 구성원이 아닌 사용자는 개봉할 수 없음")
    void non_member_cannot_open_stock() {
        Long stockId = foodStockService.register(
                actor.getId(),
                command(household.getId(), milk.getId())
        );

        User outsider = createUser();

        assertThatThrownBy(() ->
                foodStockService.open(
                        outsider.getId(),
                        new OpenFoodStockCommand(
                                household.getId(),
                                stockId
                        )
                )
        ).isInstanceOf(SecurityException.class);

        FoodStock stock = stockRepository.findById(stockId).orElseThrow();

        assertThat(stock.getOpenedAt()).isNull();

        assertThat(historyRepository.findAllByFoodStock_Id(stockId)).hasSize(1);
    }
    // todo: FoodStock.open() 수량이 0인 재고도 개봉할 수 있는 상태. (예외 정책 다듬을 때 보기.)
    // 소진된 식품을 개봉하는 행위? <- 이상해... 근데 개봉하고도 소비하지 않는 것도 이상...

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