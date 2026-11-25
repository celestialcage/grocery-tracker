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
import com.shabet.grocery.stock.domain.AcquisitionType;
import com.shabet.grocery.stock.domain.QuantityAccuracy;
import com.shabet.grocery.stock.domain.StockHistory;
import com.shabet.grocery.stock.domain.StorageType;
import com.shabet.grocery.stock.repository.FoodStockRepository;
import com.shabet.grocery.stock.repository.StockHistoryRepository;
import com.shabet.grocery.user.domain.User;
import com.shabet.grocery.user.repository.UserRepository;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class FoodStockServiceRollbackTest {

    @Autowired FoodStockService foodStockService;
    @Autowired UserRepository userRepository;
    @Autowired HouseholdRepository householdRepository;
    @Autowired HouseholdMemberRepository memberRepository;
    @Autowired FoodRepository foodRepository;
    @Autowired FoodStockRepository stockRepository;

    // 실제 Repository Bean을 Mockito Mock으로 교체
    @MockitoBean
    StockHistoryRepository historyRepository;

    // doAnswer 이거는 실무에서도 잘 안하는 수준이라고 함.
    @Test
    @DisplayName("기록 실패 시 stock까지 롤백하는지 트랜잭션 확인")
    void history_save_failure_rolls_back_stock() {

        // given: 정상적인 사용자, 가구, 식품 생성
        User user = userRepository.save(
                new User(
                        "rollback-" + UUID.randomUUID() + "@example.com",
                        "password",
                        "tester"
                )
        );

        Household household = householdRepository.save(
                new Household("테스트 가구")
        );

        memberRepository.save(
                new HouseholdMember(
                        user,
                        household,
                        HouseholdRole.OWNER,
                        Instant.now()
                )
        );

        Food food = foodRepository.save(
                Food.createSystem(
                        "우유",
                        FoodCategory.DAIRY,
                        QuantityType.VOLUME,
                        StockUnit.ML
                )
        );

        RegisterFoodStockCommand command = new RegisterFoodStockCommand(
                household.getId(),
                food.getId(),
                new BigDecimal("1000"),
                new BigDecimal("700"),
                StockUnit.ML,
                QuantityAccuracy.EXACT,
                LocalDate.of(2026, 11,22),
                LocalDate.of(2026, 11,27),
                StorageType.REFRIGERATED,
                AcquisitionType.EXISTING,
                "테스트 우유",
                null
        );

        long beforeCount =
                stockRepository.countByHousehold_Id(household.getId());

        // history 저장 시 실패하도록 설정
        // doThrow 대신 doAnswer
        /*
        * doThrow(new IllegalStateException("이력 저장 실패"))
        *           .when(historyRepository)
        *           .save(any(StockHistory.class));
        * */
        doAnswer(invocation -> {

            // 아직 같은 트랜잭션 내부이므로
            // 앞에서 INSERT한 FoodStock이 조회됨.
            assertThat(
                    stockRepository.countByHousehold_Id(household.getId())
            ).isEqualTo(beforeCount + 1);

            throw new IllegalStateException("이력 저장 실패");

        }).when(historyRepository).save(any(StockHistory.class));

        // when: service 실행 중 예외 발생
        assertThatThrownBy(()->
                foodStockService.register(user.getId(), command)
        )
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("이력 저장 실패");

        // then: history 저장은 실제로 시도되었다.
        verify(historyRepository, times(1))
                .save(any(StockHistory.class));

        // then: FoodStock INSERTS도 롤백되었다.
        assertThat(
                stockRepository.countByHousehold_Id(household.getId())
        ).isEqualTo(beforeCount);
    }

}
