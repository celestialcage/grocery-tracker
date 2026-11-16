package com.shabet.grocery.household.service;

import com.shabet.grocery.household.repository.HouseholdMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class HouseholdAccessValidator {

    private final HouseholdMemberRepository householdMemberRepository;

    public void requireMember(Long householdId, Long userId) {
        boolean isMember = householdMemberRepository.existsByHousehold_IdAndUser_Id(householdId, userId);

        if (!isMember) {
            throw new SecurityException("해당 가구에 대한 접근 권한이 없습니다.");
            // controller 단계에서 HouseholdAccessDeniedException 같은 애플리케이션 예외로 바꾸고 HTTP 403 응답과 연결
        }
    }
}
