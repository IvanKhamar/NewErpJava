package ua.newerp.api.competitors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import com.jayway.jsonpath.JsonPath;

import ua.newerp.IntegrationTest;
import ua.newerp.application.competitors.PageFetcher;
import ua.newerp.application.competitors.PageFetcher.PageFetchException;

/** Competitor pages are not downloaded: the fetcher returns invented HTML. */
@Sql({"/sql/users.sql", "/sql/orders.sql", "/sql/product-sort.sql", "/sql/competitors.sql"})
class CompetitorControllerTest extends IntegrationTest {

    @MockitoBean
    PageFetcher fetcher;

    MockHttpSession olena;

    @BeforeEach
    void logIn() {
        olena = login("olena");
    }

    @Test
    void watchesPricesAndComparesThemWithOurs() {
        int dress = id(post("/competitors", "{\"name\":\"Сукні-Плюс\",\"siteUrl\":\"https://www.dress-plus.example\",\"active\":true}"),
                201);
        int fashion = id(post("/competitors", "{\"name\":\"Мода\",\"siteUrl\":\"https://moda.example\","
                + "\"priceRegex\":\"class=\\\"cost\\\">([^<]+)\",\"active\":true}"), 201);
        assertThat(post("/competitors", "{\"name\":\"Без сайту\",\"siteUrl\":\"moda\",\"active\":true}")).hasStatus(400);
        assertThat(post("/competitors", "{\"name\":\"Вираз\",\"siteUrl\":\"https://x.example\",\"priceRegex\":\"[0-9]+\",\"active\":true}"))
                .hasStatus(400);

        int link1 = id(post("/competitors/links", link(dress, 201, "https://dress-plus.example/p/201")), 201);
        int link2 = id(post("/competitors/links", link(fashion, 201, "https://moda.example/dress-201")), 201);
        int link3 = id(post("/competitors/links", link(fashion, 202, "https://moda.example/dress-202")), 201);
        assertThat(post("/competitors/links", link(dress, 201, "https://dress-plus.example/other"))).hasStatus(400); // already there
        assertThat(post("/competitors/links", link(dress, 202, "https://elsewhere.example/p/202"))).hasStatus(400); // other site
        assertThat(post("/competitors/links", link(dress, 999, "https://dress-plus.example/p/999"))).hasStatus(404);

        when(fetcher.fetch(eq(URI.create("https://dress-plus.example/p/201")), eq("dress-plus.example")))
                .thenReturn("<meta property=\"product:price:amount\" content=\"950.00\">");
        when(fetcher.fetch(eq(URI.create("https://moda.example/dress-201")), any())).thenReturn("<i class=\"cost\">1 100 грн</i>");
        when(fetcher.fetch(eq(URI.create("https://moda.example/dress-202")), any())).thenThrow(new PageFetchException("Сайт відповів HTTP 403."));

        assertThat(mvc.post().uri("/competitors/check").session(olena)).hasStatusOk().bodyJson()
                .isLenientlyEqualTo("{\"checked\":3,\"changed\":2,\"failed\":1}");
        assertThat(mvc.get().uri("/competitors/links?productId=202").session(olena)).hasStatusOk().bodyJson().isLenientlyEqualTo("""
                [{"id":%d,"competitorName":"Мода","productName":"Сукня 202","lastPrice":null,"lastError":"Сайт відповів HTTP 403."}]
                """.formatted(link3));

        // the site blocks the check: the price is typed in by hand; the same price again adds no history row
        assertThat(post("/competitors/links/" + link3 + "/price", "{\"price\":1050}")).hasStatusOk().bodyJson()
                .isLenientlyEqualTo("{\"lastPrice\":1050.00,\"lastError\":\"\"}");
        assertThat(post("/competitors/links/" + link3 + "/price", "{\"price\":0}")).hasStatus(400);
        assertThat(mvc.post().uri("/competitors/links/" + link1 + "/check").session(olena)).hasStatusOk().bodyJson()
                .extractingPath("$.lastPrice").isEqualTo(950.0);
        assertThat(mvc.get().uri("/competitors/links/" + link1 + "/history").session(olena)).hasStatusOk().bodyJson()
                .isLenientlyEqualTo("[{\"price\":950.00,\"source\":\"auto\"}]");

        assertThat(mvc.get().uri("/competitors/report").session(olena)).hasStatusOk().bodyJson().isLenientlyEqualTo("""
                {"competitors":[{"id":%d,"name":"Мода"},{"id":%d,"name":"Сукні-Плюс"}],
                 "rows":[{"productId":201,"productName":"Сукня 201","ourPrice":1000.0000,"prices":[1100.00,950.00],"lowest":950.00,
                          "differencePercent":5.3},
                         {"productId":202,"ourPrice":1000.0000,"prices":[1050.00,null],"lowest":1050.00,"differencePercent":-4.8}]}
                """.formatted(fashion, dress));

        // a competitor we stop watching leaves the report and the check
        assertThat(mvc.put().uri("/competitors/" + fashion).session(olena).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Мода\",\"siteUrl\":\"https://moda.example\",\"active\":false}")).hasStatusOk();
        assertThat(mvc.post().uri("/competitors/check").session(olena)).bodyJson().extractingPath("$.checked").isEqualTo(1);
        assertThat(mvc.get().uri("/competitors/report").session(olena)).bodyJson().extractingPath("$.competitors.length()").isEqualTo(1);

        assertThat(mvc.delete().uri("/competitors/links/" + link2).session(olena)).hasStatus(204);
        assertThat(mvc.get().uri("/competitors").session(olena)).hasStatusOk().bodyJson()
                .isLenientlyEqualTo("[{\"name\":\"Мода\",\"links\":1,\"active\":false},{\"name\":\"Сукні-Плюс\",\"links\":1}]");
        assertThat(mvc.get().uri("/competitors/links/" + link2 + "/history").session(olena)).hasStatus(404);
        assertThat(mvc.get().uri("/competitors").session(login("keeper"))).hasStatus(403);
    }

    private static String link(int competitorId, int productId, String url) {
        return "{\"competitorId\":%d,\"productId\":%d,\"url\":\"%s\"}".formatted(competitorId, productId, url);
    }

    private int id(MvcTestResult result, int status) {
        assertThat(result).hasStatus(status);
        return JsonPath.read(new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8), "$.id");
    }

    private MvcTestResult post(String uri, String json) {
        return mvc.post().uri(uri).session(olena).contentType(MediaType.APPLICATION_JSON).content(json).exchange();
    }
}
