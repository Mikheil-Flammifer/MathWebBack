package com.mathweb.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Public view of whoever uploaded a video. Deliberately minimal:
 * no email, role, subscription or account flags.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UploaderResponse {

    private Long id;
    private String firstName;
    private String lastName;
    private String avatarUrl;
}