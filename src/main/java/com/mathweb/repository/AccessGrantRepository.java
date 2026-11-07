package com.mathweb.repository;

import com.mathweb.entity.AccessGrant;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AccessGrantRepository extends JpaRepository<AccessGrant, Long> {

    List<AccessGrant> findByUserIdOrderByCreatedAtDescIdDesc(Long userId, Pageable pageable);
}