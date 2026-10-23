package ua.newerp.web;

import ua.newerp.application.auth.Roles;

/**
 * Which roles see which section of the UI. The same rules as the API's SecurityConfig (and Access.cs of the Blazor UI).
 * Views use them in @RolesAllowed, which Vaadin checks before it opens a view.
 */
public final class Access {

    public static final String ADMIN = Roles.ADMIN;
    public static final String MANAGER = Roles.MANAGER;
    public static final String STOREKEEPER = Roles.STOREKEEPER;

    public static final String[] STOCK = {ADMIN, STOREKEEPER};
    public static final String[] ORDERS = {ADMIN, MANAGER};
    public static final String[] FINANCE = {ADMIN, MANAGER};

    private Access() {
    }
}
