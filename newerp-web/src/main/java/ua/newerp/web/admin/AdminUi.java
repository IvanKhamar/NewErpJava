package ua.newerp.web.admin;

import ua.newerp.application.auth.Roles;

/** Role names in the UI (AdminUi.cs). */
final class AdminUi {

    private AdminUi() {
    }

    static String roleLabel(String role) {
        if (role == null) {
            return "—";
        }
        return switch (role) {
            case Roles.ADMIN -> "Адміністратор";
            case Roles.MANAGER -> "Менеджер";
            case Roles.STOREKEEPER -> "Комірник";
            default -> role;
        };
    }
}
