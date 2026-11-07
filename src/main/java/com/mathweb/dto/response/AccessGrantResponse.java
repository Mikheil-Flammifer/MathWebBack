package com.mathweb.dto.response;

import com.mathweb.enums.AccessGrantAction;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccessGrantResponse {

    private Long id;
    private AccessGrantAction action;
    private Integer days;
    private String note;
    private Long grantedById;
    private String grantedByName;
    private LocalDateTime accessEndsAt;
    private LocalDateTime createdAt;
}