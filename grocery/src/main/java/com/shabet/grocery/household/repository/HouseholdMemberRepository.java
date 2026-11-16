package com.shabet.grocery.household.repository;

import com.shabet.grocery.household.domain.HouseholdMember;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HouseholdMemberRepository extends JpaRepository<HouseholdMember, Long> {

    boolean existsByHousehold_IdAndUser_Id(Long householdId, Long userId);
}
