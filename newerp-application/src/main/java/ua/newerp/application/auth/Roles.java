package ua.newerp.application.auth;

import java.util.List;

/** Roles of NewErp users. Stored in erp_user_login.role. */
public final class Roles {

    public static final String ADMIN = "Admin";
    public static final String MANAGER = "Manager";
    public static final String STOREKEEPER = "Storekeeper";

    public static final List<String> ALL = List.of(ADMIN, MANAGER, STOREKEEPER);

    private Roles() {
    }
}
