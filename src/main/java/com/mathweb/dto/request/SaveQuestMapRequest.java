package com.mathweb.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class SaveQuestMapRequest {

    @NotEmpty
    @Valid
    private List<Node> nodes;

    @NotNull
    @Valid
    private List<Edge> edges;

    @Data
    public static class Node {
        @NotNull private Long problemId;
        @NotNull private Integer x;
        @NotNull private Integer y;
        private Boolean start;
        private String nodeIcon;
    }

    @Data
    public static class Edge {
        @NotNull private Long from;
        @NotNull private Long to;
    }
}