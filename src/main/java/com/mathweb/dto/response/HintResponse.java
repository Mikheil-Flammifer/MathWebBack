package com.mathweb.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HintResponse {
    private List<String> hints;     // all hints revealed so far, in order
    private Integer hintsUsed;
    private Integer hintCount;      // total hints this problem has
}