package com.shabet.grocery.household.repository;

import com.shabet.grocery.config.JpaConfig;
import com.shabet.grocery.config.TestcontainersConfiguration;
import com.shabet.grocery.household.domain.Household;
import com.shabet.grocery.household.domain.HouseholdMember;
import com.shabet.grocery.household.domain.HouseholdRole;
import com.shabet.grocery.user.domain.User;
import com.shabet.grocery.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

// Repository와 JPA 관련 구성만 주로 로드하는 슬라이스 테스트
// 테스트 메서드마다 트랜잭션을 시작하고 종료 시 롤백하므로 repository 테스트에 적합
@DataJpaTest

@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE) // embedded db 말고 test container
@Import({TestcontainersConfiguration.class, JpaConfig.class}) // @EnableJpaAuditing도 써야하고 그래서
class HouseholdMemberRepositoryTest {

    @Autowired
    UserRepository userRepository;

    @Autowired
    HouseholdRepository householdRepository;

    @Autowired
    HouseholdMemberRepository memberRepository;

    @Test
    void householdMemberExists() {
        User user = userRepository.save(
                new User("member@test.com", "password", "tester")
        );

        Household household = householdRepository.save(
                new Household("우리집")
        );

        memberRepository.saveAndFlush(
                new HouseholdMember(
                        user,
                        household,
                        HouseholdRole.MEMBER,
                        Instant.now()
                )
        );

        boolean exists = memberRepository
                .existsByHousehold_IdAndUser_Id(
                        household.getId(),
                        user.getId()
                );

        assertThat(exists).isTrue();
    }

    // todo: 존재하지 않을 때 false 반환하는 테스트

    // todo: (household_id, user_id) 중복 등록 시 유니크 제약조건 위반 발생 테스트
}