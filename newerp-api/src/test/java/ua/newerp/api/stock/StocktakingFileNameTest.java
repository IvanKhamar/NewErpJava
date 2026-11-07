package ua.newerp.api.stock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.time.Clock;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import ua.newerp.application.common.BusinessTime;
import ua.newerp.application.stock.StocktakingService;

class StocktakingFileNameTest {

    @Test
    void fileNameHasTheKyivDateWhenUtcIsStillTheDayBefore() {
        Clock night = Clock.fixed(Instant.parse("2026-11-06T23:30:00Z"), BusinessTime.ZONE);
        var controller = new StocktakingController(mock(StocktakingService.class), night);

        var response = controller.sheet(null, null);

        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION)).contains("inventaryzatsiia-2026-11-07.xlsx");
    }
}
