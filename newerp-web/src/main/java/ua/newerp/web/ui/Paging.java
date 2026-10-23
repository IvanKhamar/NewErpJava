package ua.newerp.web.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntFunction;

/**
 * A grid asks for rows by offset and count, the services give numbered pages: this joins the pages that cover the asked rows.
 */
public final class Paging {

    private Paging() {
    }

    /** @param page gets a page by its number from 1 */
    public static <T> List<T> rows(int offset, int limit, int pageSize, IntFunction<List<T>> page) {
        var rows = new ArrayList<T>();
        int number = offset / pageSize;
        int skip = offset % pageSize;
        while (rows.size() < skip + limit) {
            List<T> items = page.apply(number + 1);
            rows.addAll(items);
            if (items.size() < pageSize) {
                break;
            }
            number++;
        }
        return rows.subList(Math.min(skip, rows.size()), Math.min(skip + limit, rows.size()));
    }
}
