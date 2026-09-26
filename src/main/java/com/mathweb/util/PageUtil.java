package com.mathweb.util;

import com.mathweb.dto.response.PageResponse;
import org.springframework.data.domain.Page;

import java.util.function.Function;
import java.util.stream.Collectors;

public class PageUtil {

    private PageUtil() {}

    public static <T, R> PageResponse<R> toPageResponse(
            Page<T> page, Function<T, R> mapper) {
        return PageResponse.<R>builder()
                .content(page.getContent().stream()
                        .map(mapper)
                        .collect(Collectors.toList()))
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .first(page.isFirst())
                .last(page.isLast())
                .build();
    }
}