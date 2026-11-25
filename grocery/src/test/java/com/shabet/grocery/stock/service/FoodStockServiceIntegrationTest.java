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
import com.shabet.grocery.stock.dto.FoodStockDetailResponse;
import com.shabet.grocery.stock.dto.FoodStockListResponse;
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
import java.util.NoSuchElementException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Slf4j
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class FoodStockServiceIntegrationTest {

    @Autowired
    FoodStockService foodStockService;
    @Autowired
    UserRepository userRepository;
    @Autowired
    HouseholdRepository householdRepository;
    @Autowired
    HouseholdMemberRepository memberRepository;
    @Autowired
    FoodRepository foodRepository;
    @Autowired
    FoodStockRepository stockRepository;
    @Autowired
    StockHistoryRepository historyRepository;

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

        assertThatThrownBy(() ->
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
                .filter(h -> h.getType() == StockHistoryType.CONSUMED)
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
                .filter(h -> h.getType() == StockHistoryType.DISCARDED)
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
                        .filter(h -> h.getType() == StockHistoryType.ADJUSTED)
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

    // 정상 상세 조회
    @Test
    @DisplayName("상세 조회 시 Food 이름, 카테고리, 재고 수량, 소비 기한이 정확한지 확인")
    void get_detail_works_properly() {
        RegisterFoodStockCommand cmd =
                command(household.getId(), milk.getId());

        Long stockId = foodStockService.register(actor.getId(), cmd);

//        FoodStock stock = stockRepository.findById(stockId).orElseThrow();

        FoodStockDetailResponse response = foodStockService.getDetail(
                actor.getId(), household.getId(), stockId
        );

        // 테스트의 입력값을 기대값으로 활용하자
        assertThat(response.stockId()).isEqualTo(stockId);
        assertThat(response.foodName()).isEqualTo(milk.getName());
        assertThat(response.foodCategory()).isEqualTo(milk.getCategory());

        assertThat(response.currentQuantity()).isEqualByComparingTo(
//                stock.getCurrentQuantity()
                cmd.currentQuantity()
        );

        assertThat(response.expiresAt()).isEqualTo(
//                stock.getExpiresAt()
                cmd.expiresAt()
        );
    }

    // 다른 Household의 재고 조회
    @Test
    @DisplayName("구성원이더라도 요청한 Household에 속하지 않는 재고는 조회할 수 없어야 함")
    void cannot_get_other_households_detail() {

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

        Long stockId = foodStockService.register(
                otherUser.getId(),
                command(otherHousehold.getId(), milk.getId())
        );

        assertThatThrownBy(() ->
                foodStockService.getDetail(actor.getId(), household.getId(), stockId)
        ).isInstanceOf(NoSuchElementException.class);
    }

    // 비구성원 접근
    @Test
    @DisplayName("Household 구성원이 아닌 사용자는 조회 거부")
    void outsider_cannot_get_households_detail() {
        Long stockId = foodStockService.register(
                actor.getId(),
                command(household.getId(), milk.getId())
        );

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

        assertThatThrownBy(() ->
                foodStockService.getDetail(
                        otherUser.getId(), household.getId(), stockId
                )
        ).isInstanceOf(SecurityException.class);
    }

    // 소진된 재고 조회
    @Test
    @DisplayName("수량이 0이어도 상세 조회가 가능하며 depletionReason 확인")
    void get_depleted_stock_details_and_reason() {
        Long stockId = foodStockService.register(
                actor.getId(), command(household.getId(), milk.getId())
        );

        // adjust
        foodStockService.adjust(
                actor.getId(),
                new AdjustFoodStockCommand(
                        household.getId(),
                        stockId,
                        BigDecimal.ZERO,
                        QuantityAccuracy.EXACT
                )
        );

        FoodStockDetailResponse response = foodStockService.getDetail(
                actor.getId(), household.getId(), stockId
        );

        assertThat(response.currentQuantity()).isEqualByComparingTo(BigDecimal.ZERO);

        assertThat(response.depletionReason()).isEqualTo(DepletionReason.ADJUSTED);

        assertThat(response.depletionReason()).isNotNull();
    }

    // 리스트 조회? 우유, 계란, 두부
    @Test
    @DisplayName("식품 재고 리스트 조회")
    void get_list_different_foods() {
        Food egg = foodRepository.save(
                Food.createSystem(
                        "계란",
                        FoodCategory.EGG,
                        QuantityType.COUNT,
                        StockUnit.EA
                )
        );

        Food dubu = foodRepository.save(
                Food.createSystem(
                        "두부",
                        FoodCategory.ETC, // 무슨 카테고리가 적합한지 고민...
                        QuantityType.PORTION,
                        StockUnit.BLOCK
                )
        );

        Long milkStockId = foodStockService.register(
                actor.getId(), command(household.getId(), milk.getId())
        );

        Long eggStockId = foodStockService.register(
                actor.getId(), new RegisterFoodStockCommand(
                        household.getId(),
                        egg.getId(),
                        new BigDecimal("30"),
                        new BigDecimal("30"),
                        StockUnit.EA,
                        QuantityAccuracy.EXACT,
                        LocalDate.of(2026, 11, 24),
                        LocalDate.of(2026, 12, 10),
                        StorageType.REFRIGERATED,
                        AcquisitionType.PURCHASED,
                        "초록농장 계란",
                        "사온 계란 한 판"
                )
        );

        Long dubuStockId = foodStockService.register(
                actor.getId(), new RegisterFoodStockCommand(
                        household.getId(),
                        dubu.getId(),
                        new BigDecimal("1"),
                        new BigDecimal("0.5"),
                        StockUnit.BLOCK,
                        QuantityAccuracy.ESTIMATED,
                        LocalDate.of(2026, 11, 24),
                        LocalDate.of(2026, 11, 28),
                        StorageType.REFRIGERATED,
                        AcquisitionType.EXISTING,
                        "풀무원 두부",
                        "반모 남은 두부"
                )
        );

        List<FoodStockListResponse> stockList = foodStockService.getList(actor.getId(), household.getId());

        assertThat(stockList.size()).isEqualTo(3);

        // 목록에 정확한 재고가 포함되고 ID 내림차순인지 확인(id 내림차순도...?)
        // 순서 중요하지 않으면 containsExactlyInAnyOrder()도 사용 가능.
        assertThat(stockList)
                .extracting(FoodStockListResponse::stockId)
                .containsExactly(dubuStockId, eggStockId, milkStockId);

        // 두부 정보 검증
        FoodStockListResponse dubuResponse = stockList.get(0);

        assertThat(dubuResponse.foodName()).isEqualTo("두부");
        assertThat(dubuResponse.currentQuantity()).isEqualByComparingTo("0.5");
        assertThat(dubuResponse.quantityAccuracy()).isEqualTo(QuantityAccuracy.ESTIMATED);
        assertThat(dubuResponse.expiresAt()).isEqualTo(LocalDate.of(2026, 11, 28));

        // 계란 정보 검증
        FoodStockListResponse eggResponse = stockList.get(1);

        assertThat(eggResponse.foodCategory()).isEqualTo(FoodCategory.EGG);
        assertThat(eggResponse.currentQuantity()).isEqualByComparingTo("30");
    }

    // 다른 집 데이터 노출 방지
    @Test
    @DisplayName("목록 조회 시 다른 Household 재고는 제외")
    void get_list_only_own_household() {
        // given
        Household otherHousehold = householdRepository.save(
                new Household("별도 가구")
        );

        memberRepository.save(
                new HouseholdMember(
                        actor,
                        otherHousehold,
                        HouseholdRole.OWNER,
                        Instant.now()
                )
        );

        Long ownStockId = foodStockService.register(
                actor.getId(), command(household.getId(), milk.getId())
        );

        Long otherStockId = foodStockService.register(
                actor.getId(),
                command(otherHousehold.getId(), milk.getId())
        );

        // when
        List<FoodStockListResponse> result = foodStockService.getList(
                actor.getId(), household.getId()
        );

        // then
        assertThat(result)
                .extracting(FoodStockListResponse::stockId)
                .containsExactly(ownStockId)
                .doesNotContain(otherStockId);
    }

    @Test
    @DisplayName("비구성원은 재고 목록 조회 불가")
    void outsider_cannot_get_stock_list() {
        // given
        User outsider = createUser();

        // when & then
        assertThatThrownBy(() ->
                foodStockService.getList(outsider.getId(), household.getId())
        ).isInstanceOf(SecurityException.class);
    }

    @Test
    @DisplayName("재고가 없는 household에서는 빈 목록 반환")
    void get_empty_stock_list() {
        // when
        List<FoodStockListResponse> result = foodStockService.getList(
                actor.getId(), household.getId()
        );

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("소진된 재고도 목록 조회에 포함")
    void get_list_includes_depleted_stock() {
        // given
        Long stockId = foodStockService.register(
                actor.getId(), command(household.getId(), milk.getId())
        );

        foodStockService.adjust(
                actor.getId(),
                new AdjustFoodStockCommand(
                        household.getId(),
                        stockId,
                        BigDecimal.ZERO,
                        QuantityAccuracy.EXACT
                )
        );

        // when
        List<FoodStockListResponse> result = foodStockService.getList(
                actor.getId(), household.getId()
        );

        // then
        // singleElement - 리스트에 정확히 하나의 요소가 있어야 한다고 검증.
        assertThat(result).singleElement().satisfies(stock -> {
            assertThat(stock.stockId()).isEqualTo(stockId);
            assertThat(stock.currentQuantity()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(stock.depletedAt()).isNotNull();
        });
    }

    @Test
    @DisplayName("식품명 우유로 검색 시 우유 재고 반환")
    void search_food_name_milk() {
        // given - 세가지 식품
        Food egg = foodRepository.save(
                Food.createSystem(
                        "계란",
                        FoodCategory.EGG,
                        QuantityType.COUNT,
                        StockUnit.EA
                )
        );

        Food dubu = foodRepository.save(
                Food.createSystem(
                        "두부",
                        FoodCategory.ETC,
                        QuantityType.PORTION,
                        StockUnit.BLOCK
                )
        );

        Long milkStockId = foodStockService.register(
                actor.getId(), command(household.getId(), milk.getId())
        );

        Long eggStockId = foodStockService.register(
                actor.getId(), new RegisterFoodStockCommand(
                        household.getId(),
                        egg.getId(),
                        new BigDecimal("30"),
                        new BigDecimal("30"),
                        StockUnit.EA,
                        QuantityAccuracy.EXACT,
                        LocalDate.of(2026, 11, 24),
                        LocalDate.of(2026, 12, 10),
                        StorageType.REFRIGERATED,
                        AcquisitionType.PURCHASED,
                        "초록농장 계란",
                        "사온 계란 한 판"
                )
        );

        Long dubuStockId = foodStockService.register(
                actor.getId(), new RegisterFoodStockCommand(
                        household.getId(),
                        dubu.getId(),
                        new BigDecimal("1"),
                        new BigDecimal("0.5"),
                        StockUnit.BLOCK,
                        QuantityAccuracy.ESTIMATED,
                        LocalDate.of(2026, 11, 24),
                        LocalDate.of(2026, 11, 28),
                        StorageType.REFRIGERATED,
                        AcquisitionType.EXISTING,
                        "풀무원 두부",
                        "반모 남은 두부"
                )
        );

        // when
        List<FoodStockListResponse> result =
                foodStockService.search(actor.getId(), household.getId(), "우유");

        // then
        assertThat(result).singleElement().satisfies(stock -> {
            assertThat(stock.stockId()).isEqualTo(milkStockId);
            assertThat(stock.currentQuantity()).isEqualByComparingTo("650");
            assertThat(stock.foodName()).isEqualTo("우유");
        });
    }

    @Test
    @DisplayName("상품명 풀무원으로 검색 시 두부 재고 반환")
    void search_product_name_dubu() {
        // given
        Food egg = foodRepository.save(
                Food.createSystem(
                        "계란",
                        FoodCategory.EGG,
                        QuantityType.COUNT,
                        StockUnit.EA
                )
        );

        Food dubu = foodRepository.save(
                Food.createSystem(
                        "두부",
                        FoodCategory.ETC,
                        QuantityType.PORTION,
                        StockUnit.BLOCK
                )
        );

        Long milkStockId = foodStockService.register(
                actor.getId(), command(household.getId(), milk.getId())
        );

        Long eggStockId = foodStockService.register(
                actor.getId(), new RegisterFoodStockCommand(
                        household.getId(),
                        egg.getId(),
                        new BigDecimal("30"),
                        new BigDecimal("30"),
                        StockUnit.EA,
                        QuantityAccuracy.EXACT,
                        LocalDate.of(2026, 11, 24),
                        LocalDate.of(2026, 12, 10),
                        StorageType.REFRIGERATED,
                        AcquisitionType.PURCHASED,
                        "초록농장 계란",
                        "사온 계란 한 판"
                )
        );

        Long dubuStockId = foodStockService.register(
                actor.getId(), new RegisterFoodStockCommand(
                        household.getId(),
                        dubu.getId(),
                        new BigDecimal("1"),
                        new BigDecimal("0.5"),
                        StockUnit.BLOCK,
                        QuantityAccuracy.ESTIMATED,
                        LocalDate.of(2026, 11, 24),
                        LocalDate.of(2026, 11, 28),
                        StorageType.REFRIGERATED,
                        AcquisitionType.EXISTING,
                        "풀무원 두부",
                        "반모 남은 두부"
                )
        );

        // when
        List<FoodStockListResponse> result =
                foodStockService.search(actor.getId(), household.getId(), "풀무원");

        // then
        assertThat(result).singleElement().satisfies(stock -> {
            assertThat(stock.stockId()).isEqualTo(dubuStockId);
            assertThat(stock.currentQuantity()).isEqualByComparingTo("0.5");
            assertThat(stock.foodName()).isEqualTo("두부");
            assertThat(stock.productLabel()).isEqualTo("풀무원 두부");
        });
    }

    @Test
    @DisplayName("검색어가 null이나 공백이면 household 전체 재고 반환")
    void search_null_or_blank() {
        // given
        Food egg = foodRepository.save(
                Food.createSystem(
                        "계란",
                        FoodCategory.EGG,
                        QuantityType.COUNT,
                        StockUnit.EA
                )
        );

        Food dubu = foodRepository.save(
                Food.createSystem(
                        "두부",
                        FoodCategory.ETC,
                        QuantityType.PORTION,
                        StockUnit.BLOCK
                )
        );

        Long milkStockId = foodStockService.register(
                actor.getId(), command(household.getId(), milk.getId())
        );

        Long eggStockId = foodStockService.register(
                actor.getId(), new RegisterFoodStockCommand(
                        household.getId(),
                        egg.getId(),
                        new BigDecimal("30"),
                        new BigDecimal("30"),
                        StockUnit.EA,
                        QuantityAccuracy.EXACT,
                        LocalDate.of(2026, 11, 24),
                        LocalDate.of(2026, 12, 10),
                        StorageType.REFRIGERATED,
                        AcquisitionType.PURCHASED,
                        "초록농장 계란",
                        "사온 계란 한 판"
                )
        );

        Long dubuStockId = foodStockService.register(
                actor.getId(), new RegisterFoodStockCommand(
                        household.getId(),
                        dubu.getId(),
                        new BigDecimal("1"),
                        new BigDecimal("0.5"),
                        StockUnit.BLOCK,
                        QuantityAccuracy.ESTIMATED,
                        LocalDate.of(2026, 11, 24),
                        LocalDate.of(2026, 11, 28),
                        StorageType.REFRIGERATED,
                        AcquisitionType.EXISTING,
                        "풀무원 두부",
                        "반모 남은 두부"
                )
        );

        // when
        List<FoodStockListResponse> result =
                foodStockService.search(actor.getId(), household.getId(), null);

        // then
        assertThat(result.size()).isEqualTo(3);

        assertThat(result)
                .extracting(FoodStockListResponse::stockId)
                .containsExactly(dubuStockId, eggStockId, milkStockId);

        FoodStockListResponse dubuResponse = result.get(0);

        assertThat(dubuResponse.foodName()).isEqualTo("두부");
        assertThat(dubuResponse.currentQuantity()).isEqualByComparingTo("0.5");
        assertThat(dubuResponse.quantityAccuracy()).isEqualTo(QuantityAccuracy.ESTIMATED);
        assertThat(dubuResponse.expiresAt()).isEqualTo(LocalDate.of(2026, 11, 28));

        FoodStockListResponse eggResponse = result.get(1);

        assertThat(eggResponse.foodName()).isEqualTo("계란");
        assertThat(eggResponse.foodCategory()).isEqualTo(FoodCategory.EGG);
        assertThat(eggResponse.currentQuantity()).isEqualByComparingTo("30");
        assertThat(eggResponse.quantityAccuracy()).isEqualTo(QuantityAccuracy.EXACT);
    }

    @Test
    @DisplayName("다른 household 검색 결과 섞이지 않는지 확인")
    void search_only_own_household() {
        // given
        Household otherHousehold = householdRepository.save(
                new Household("딴 집")
        );

        memberRepository.save(
                new HouseholdMember(
                        actor,
                        otherHousehold,
                        HouseholdRole.OWNER,
                        Instant.now()
                )
        );

        Long ownStockId = foodStockService.register(
                actor.getId(), command(household.getId(), milk.getId())
        );

        Long otherStockId = foodStockService.register(
                actor.getId(), command(otherHousehold.getId(), milk.getId())
        );

        // when
        List<FoodStockListResponse> result =
                foodStockService.search(actor.getId(), household.getId(), null);

        // then
        assertThat(result)
                .extracting(FoodStockListResponse::stockId)
                .containsExactly(ownStockId)
                .doesNotContain(otherStockId);
    }

    @Test
    @DisplayName("비구성원 검색 요청 거부")
    void outsider_cannot_search() {
        // given
        User outsider = createUser();

        // when & then
        assertThatThrownBy(() ->
                foodStockService.search(outsider.getId(), household.getId(), null)
        ).isInstanceOf(SecurityException.class);
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