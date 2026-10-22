package ua.newerp.application.common;

import java.util.List;

/** One page of a list. totalPages is a real component, so it gets into the JSON like in the .NET version. */
public record PagedResult<T>(List<T> items, int page, int pageSize, int totalCount, int totalPages) {

    public static <T> PagedResult<T> of(List<T> items, int page, int pageSize, int totalCount) {
        return new PagedResult<>(items, page, pageSize, totalCount, (int) Math.ceil(totalCount / (double) pageSize));
    }
}
