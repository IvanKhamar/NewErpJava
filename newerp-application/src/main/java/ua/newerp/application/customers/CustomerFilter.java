package ua.newerp.application.customers;

/** Query string filters of the customer list; page size 20 by default. */
public record CustomerFilter(String search, Integer groupId, Boolean status, Integer page, Integer pageSize) {

    public static final int DEFAULT_PAGE_SIZE = 20;
}
