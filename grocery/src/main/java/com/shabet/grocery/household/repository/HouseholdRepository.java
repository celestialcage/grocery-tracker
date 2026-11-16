package com.shabet.grocery.household.repository;

import com.shabet.grocery.household.domain.Household;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HouseholdRepository extends JpaRepository<Household, Long> {
}
