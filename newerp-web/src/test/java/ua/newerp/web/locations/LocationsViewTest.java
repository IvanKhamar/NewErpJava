package ua.newerp.web.locations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.vaadin.browserless.BrowserlessTest;
import com.vaadin.browserless.ViewPackages;
import com.vaadin.flow.component.UI;

import ua.newerp.application.locations.LocationDto;
import ua.newerp.application.locations.LocationImportResult;
import ua.newerp.application.locations.LocationService;
import ua.newerp.application.locations.SaveLocationRequest;

/** The locations page with a mocked service; the addresses are invented. */
@ViewPackages(classes = LocationsView.class)
class LocationsViewTest extends BrowserlessTest {

    final LocationService service = mock(LocationService.class);
    final LocationDto podil = new LocationDto(3, "Поділ", "", "Ірина", "+380671112233", "Київ", "вул. Сагайдачного, 1", "пункт видачі");

    @Test
    void editsTheClickedEntryAndCreatesANewOne() {
        when(service.list("")).thenReturn(List.of(podil));
        when(service.create(new SaveLocationRequest("Склад", "", "", "", "Львів", "", "склад"))).thenReturn(4);
        var view = new LocationsView(service);
        UI.getCurrent().add(view);

        view.edit(podil);
        test(view.city).setValue("Київ ");
        test(view.save).click();
        verify(service).update(3, new SaveLocationRequest("Поділ", "", "Ірина", "+380671112233", "Київ ", "вул. Сагайдачного, 1",
                "пункт видачі"));

        test(view.newEntry).click();
        test(view.name).setValue("Склад");
        test(view.city).setValue("Львів");
        test(view.type).setValue("склад");
        test(view.save).click();
        assertThat(view.editId).isEqualTo(4);
    }

    @Test
    void importErrorsAreShownAndNothingElse() {
        when(service.list("")).thenReturn(List.of());
        byte[] file = {1, 2, 3};
        when(service.importFile(file)).thenReturn(new LocationImportResult(0, 0, List.of("Рядок 2: немає назви.")));
        var view = new LocationsView(service);
        UI.getCurrent().add(view);

        view.importFile(file);

        assertThat(view.importErrors.getElement().getTextRecursively()).contains("Рядок 2: немає назви.");
    }
}
