package com.com.manasuniversityecosystem.api.v1.common;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Standardised pagination envelope.
 *
 * {
 *   "items": [...],
 *   "page": 0,
 *   "size": 10,
 *   "total_elements": 42,
 *   "total_pages": 5,
 *   "has_next": true,
 *   "has_previous": false
 * }
 */
public record PageResponse<T>(

        @JsonProperty("items")          List<T>  items,
        @JsonProperty("page")           int      page,
        @JsonProperty("size")           int      size,
        @JsonProperty("total_elements") long     totalElements,
        @JsonProperty("total_pages")    int      totalPages,
        @JsonProperty("has_next")       boolean  hasNext,
        @JsonProperty("has_previous")   boolean  hasPrevious

) {
    public static <T> PageResponse<T> from(Page<T> springPage) {
        return new PageResponse<>(
                springPage.getContent(),
                springPage.getNumber(),
                springPage.getSize(),
                springPage.getTotalElements(),
                springPage.getTotalPages(),
                springPage.hasNext(),
                springPage.hasPrevious()
        );
    }
}