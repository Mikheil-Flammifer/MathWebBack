package com.mathweb.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class SaveQuestMapRequest {

    @NotEmpty(message = "A map needs at least one node")
    @Size(max = 200, message = "A map can have at most 200 nodes")
    @Valid
    private List<Node> nodes;

    @NotNull(message = "Edges list is required (can be empty)")
    @Size(max = 1000, message = "A map can have at most 1000 links")
    @Valid
    private List<Edge> edges;

    @Data
    public static class Node {
        @NotNull private Long problemId;

        @NotNull
        @Min(value = -100000, message = "Position is out of range")
        @Max(value = 100000, message = "Position is out of range")
        private Integer x;

        @NotNull
        @Min(value = -100000, message = "Position is out of range")
        @Max(value = 100000, message = "Position is out of range")
        private Integer y;

        private Boolean start;

        @Size(max = 50, message = "Node icon is too long (max 50 characters)")
        private String nodeIcon;
    }

    @Data
    public static class Edge {
        @NotNull private Long from;
        @NotNull private Long to;
    }
}