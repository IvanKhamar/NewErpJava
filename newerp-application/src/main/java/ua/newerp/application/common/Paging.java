package ua.newerp.application.common;

/** Brings page and size to allowed limits, so a client cannot ask for a million rows. */
public record Paging(int page, int pageSize) {

    public static final int MAX_PAGE_SIZE = 100;

    public static Paging normalize(Integer page, Integer pageSize) {
        int p = page == null ? 1 : Math.max(page, 1);
        int size = pageSize == null ? 20 : Math.clamp(pageSize, 1, MAX_PAGE_SIZE);
        return new Paging(p, size);
    }

    public int offset() {
        return (page - 1) * pageSize;
    }
}
