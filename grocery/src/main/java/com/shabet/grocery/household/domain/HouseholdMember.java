package com.shabet.grocery.household.domain;

import com.shabet.grocery.user.domain.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(
        name = "household_members",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_household_members_household_user",
                        columnNames = {"household_id", "user_id"}
                )
        },
        indexes = {
                @Index(
                        name = "idx_household_members_user_id",
                        columnList = "user_id"
                )
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class HouseholdMember {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "household_member_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "user_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_household_members_user")
    )
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "household_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_household_members_household")
    )
    private Household household;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private HouseholdRole role;

    @Column(nullable = false)
    private Instant joinedAt;

    public HouseholdMember(User user, Household household, HouseholdRole role, Instant joinedAt) {
        this.user = user;
        this.household = household;
        this.role = role;
        this.joinedAt = joinedAt;
    }

    public boolean isOwner() {
        return role == HouseholdRole.OWNER;
    }
}
