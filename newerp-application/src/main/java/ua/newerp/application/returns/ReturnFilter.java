package ua.newerp.application.returns;

/** Query string filters of the return list; page size 20 by default. search: customer, phone, product or model. */
public record ReturnFilter(Integer returnId, Integer orderId, Integer statusId, String search, Integer page, Integer pageSize) {

    public static final int DEFAULT_PAGE_SIZE = 20;
}
