package ua.newerp.web.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.vaadin.browserless.BrowserlessTest;
import com.vaadin.browserless.ViewPackages;
import com.vaadin.flow.component.UI;

import ua.newerp.application.auth.CreateUserRequest;
import ua.newerp.application.auth.CurrentUser;
import ua.newerp.application.auth.Roles;
import ua.newerp.application.auth.SetAccessRequest;
import ua.newerp.application.auth.UserAccessResult;
import ua.newerp.application.auth.UserAdminService;
import ua.newerp.application.auth.UserDto;
import ua.newerp.application.staff.BonusReport;
import ua.newerp.application.staff.BonusService;
import ua.newerp.application.staff.ManagerBonusRow;
import ua.newerp.web.auth.CurrentSession;

/** The users and bonuses pages with mocked services; the people are invented. */
@ViewPackages(packages = "ua.newerp.web.noroutes")
class AdminViewsTest extends BrowserlessTest {

    final UserAdminService users = mock(UserAdminService.class);
    final CurrentSession session = mock(CurrentSession.class);
    final UserDto admin = new UserDto(1, "admin", "Адмін Тест", "", true, true, Roles.ADMIN, LocalDateTime.of(2026, 10, 23, 9, 0));
    final UserDto olena = new UserDto(7, "olena", "Олена Коваль", "", true, true, Roles.MANAGER, null);
    final UserDto andriy = new UserDto(8, "andriy", "Андрій Бондар", "", true, false, null, null);

    @BeforeEach
    void setUp() {
        when(session.user()).thenReturn(Optional.of(new CurrentUser(1, "admin", "Адмін Тест", Roles.ADMIN, false)));
        when(users.list()).thenReturn(List.of(admin, olena, andriy));
    }

    @Test
    void listsOnlyUsersWithAccessUntilTheSwitchIsOff() {
        var view = new UsersView(users, session);
        UI.getCurrent().add(view);
        assertThat(view.grid.getListDataView().getItemCount()).isEqualTo(2);

        test(view.withAccessOnly).click();

        assertThat(view.grid.getListDataView().getItemCount()).isEqualTo(3);
    }

    @Test
    void givingAccessShowsTheTemporaryPasswordOnce() {
        when(users.setAccess(1, 8, new SetAccessRequest(Roles.STOREKEEPER, null))).thenReturn(new UserAccessResult(8, "Tmp-4821-kx"));
        var view = new UsersView(users, session);
        UI.getCurrent().add(view);

        view.setRole(andriy, Roles.STOREKEEPER);

        verify(users).setAccess(1, 8, new SetAccessRequest(Roles.STOREKEEPER, null));
        var dialog = find(TemporaryPasswordDialog.class).single();
        assertThat(dialog.password.getText()).isEqualTo("Tmp-4821-kx");
    }

    @Test
    void anAdminCannotPickAnotherRoleForThemselves() {
        var dialog = new RoleDialog("Роль: Адмін Тест", Roles.ADMIN, true, role -> { });
        dialog.open();

        assertThat(dialog.role.getItemEnabledProvider().test(Roles.MANAGER)).isFalse();
        assertThat(dialog.save.isEnabled()).isFalse();
    }

    @Test
    void newEmployeeGetsTheChosenRole() {
        var request = new CreateUserRequest("iryna", "Ірина", "Шевчук", "", Roles.MANAGER, null);
        when(users.create(request)).thenReturn(new UserAccessResult(9, "Tmp-1111-ab"));
        var created = new UserAccessResult[1];
        var dialog = new NewUserDialog(users, result -> created[0] = result);
        dialog.open();
        roundTrip();
        test(dialog.firstName).setValue("Ірина");
        test(dialog.lastName).setValue("Шевчук");
        test(dialog.username).setValue("iryna");

        test(dialog.create).click();

        assertThat(created[0].temporaryPassword()).isEqualTo("Tmp-1111-ab");
    }

    @Test
    void bonusesShowTheRateAndTotal() {
        var bonuses = mock(BonusService.class);
        LocalDate first = LocalDate.now().withDayOfMonth(1);
        when(bonuses.calculate(first, LocalDate.now())).thenReturn(BonusReport.of(first, LocalDate.now(), new BigDecimal("2"),
                List.of(new ManagerBonusRow(7, "Олена Коваль", 3, new BigDecimal("5000"), new BigDecimal("100")))));

        var view = new BonusesView(bonuses);

        assertThat(view.total.getText()).contains("100");
        assertThat(view.grid.getListDataView().getItemCount()).isEqualTo(1);
    }
}
