package com.medcore.api.common.web;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

@Schema(description = "Paginated response envelope")
public record PageResponse<T>(
        @Schema(description = "Page content") List<T> content,
        @Schema(description = "Zero-based page index", example = "0") int page,
        @Schema(description = "Page size", example = "20") int size,
        @Schema(description = "Total number of elements", example = "137") long totalElements,
        @Schema(description = "Total number of pages", example = "7") int totalPages) {

    public static <E, T> PageResponse<T> of(Page<E> page, Function<E, T> mapper) {
        return new PageResponse<>(page.getContent().stream().map(mapper).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }
}
