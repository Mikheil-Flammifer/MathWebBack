package com.mathweb.repository;

import com.mathweb.entity.User;
import com.mathweb.enums.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    long countByRoleAndActiveTrue(Role role);

    List<User> findByRole(Role role);

    List<User> findByActiveTrue();

    @Query("SELECT u FROM User u WHERE u.emailVerified = false AND u.active = true")
    List<User> findUnverifiedUsers();

    @Query("SELECT u.id FROM User u WHERE u.role = :role AND u.active = true AND u.emailVerified = true")
    List<Long> findActiveVerifiedIdsByRole(@Param("role") Role role);

    @Query(value = """
           select u from User u
           left join fetch u.subscription s
           where (:role is null or u.role = :role)
             and (:active is null or u.active = :active)
             and (lower(u.email) like lower(concat('%', :keyword, '%'))
                  or lower(u.firstName) like lower(concat('%', :keyword, '%'))
                  or lower(u.lastName) like lower(concat('%', :keyword, '%')))
             and (:hasAccess is null
                  or (:hasAccess = true
                      and s.status = 'ACTIVE' and s.currentPeriodEnd > :now)
                  or (:hasAccess = false
                      and (s.id is null or s.status <> 'ACTIVE'
                           or s.currentPeriodEnd is null or s.currentPeriodEnd <= :now)))
           """,
            countQuery = """
           select count(u) from User u
           left join u.subscription s
           where (:role is null or u.role = :role)
             and (:active is null or u.active = :active)
             and (lower(u.email) like lower(concat('%', :keyword, '%'))
                  or lower(u.firstName) like lower(concat('%', :keyword, '%'))
                  or lower(u.lastName) like lower(concat('%', :keyword, '%')))
             and (:hasAccess is null
                  or (:hasAccess = true
                      and s.status = 'ACTIVE' and s.currentPeriodEnd > :now)
                  or (:hasAccess = false
                      and (s.id is null or s.status <> 'ACTIVE'
                           or s.currentPeriodEnd is null or s.currentPeriodEnd <= :now)))
           """)
    Page<User> searchForAdmin(@Param("keyword") String keyword,
                              @Param("role") Role role,
                              @Param("active") Boolean active,
                              @Param("hasAccess") Boolean hasAccess,
                              @Param("now") LocalDateTime now,
                              Pageable pageable);
}