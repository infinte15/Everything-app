package com.Finn.everything_app.dto;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/**
 * Schlanke Seiten-Huelle fuer paginierte Endpunkte.
 *
 * <p>Bewusst nicht {@code Page}/{@code PageImpl} direkt serialisieren: Spring Boot 3.2 warnt
 * davor, weil deren JSON-Form kein stabiler Vertrag ist.
 */
public record PagedResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean last
) {
    public static <E, D> PagedResponse<D> of(Page<E> page, Function<E, D> mapper) {
        return new PagedResponse<>(
                page.getContent().stream().map(mapper).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isLast()
        );
    }
}
