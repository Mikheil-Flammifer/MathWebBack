package com.mathweb.entity;

import com.mathweb.enums.AccessGrantAction;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "access_grants")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AccessGrant extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "granted_by")
    private Long grantedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 20)
    private AccessGrantAction action;

    @Column(name = "days")
    private Integer days;

    @Column(name = "note", length = 500)
    private String note;

    @Column(name = "period_end_after")
    private LocalDateTime periodEndAfter;
}