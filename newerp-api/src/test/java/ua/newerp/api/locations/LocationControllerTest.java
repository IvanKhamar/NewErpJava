package ua.newerp.api.locations;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import com.jayway.jsonpath.JsonPath;

import ua.newerp.IntegrationTest;

@Sql({"/sql/users.sql", "/sql/locations.sql"})
class LocationControllerTest extends IntegrationTest {

    @Test
    void crudAndExcelRoundTrip() throws IOException {
        MockHttpSession olena = login("olena");

        MvcTestResult created = post(olena, "{\"name\":\"Склад на Подолі\",\"city\":\"Київ\",\"telephone\":\"0671234567\"}");
        assertThat(created).hasStatus(201);
        int other = JsonPath.read(body(post(olena, "{\"name\":\"Тимчасовий\"}")), "$.id");
        assertThat(mvc.delete().uri("/locations/" + other).session(olena)).hasStatus(204);
        assertThat(post(olena, "{\"name\":\" \"}")).hasStatus(400);

        // The exported file, after a name is edited, imports back: the record with the same id is updated.
        MvcTestResult export = mvc.get().uri("/locations/export.xlsx").session(olena).exchange();
        assertThat(export).hasStatusOk();
        byte[] edited;
        try (var book = new XSSFWorkbook(new ByteArrayInputStream(export.getResponse().getContentAsByteArray()))) {
            Sheet sheet = book.getSheetAt(0);
            assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("ID");
            assertThat(sheet.getRow(1).getCell(4).getStringCellValue()).isEqualTo("0671234567");
            sheet.getRow(1).getCell(1).setCellValue("Склад на Подолі (новий)");
            var row = sheet.createRow(2);
            row.createCell(1).setCellValue("Пункт видачі у Львові");
            row.createCell(5).setCellValue("Львів");
            var output = new ByteArrayOutputStream();
            book.write(output);
            edited = output.toByteArray();
        }

        assertThat(mvc.post().uri("/locations/import").session(olena).contentType(MediaType.APPLICATION_OCTET_STREAM).content(edited))
                .hasStatusOk().bodyJson().isStrictlyEqualTo("{\"added\":1,\"updated\":1,\"errors\":[]}");
        MvcTestResult list = mvc.get().uri("/locations").session(olena).exchange();
        assertThat(list).bodyJson().extractingPath("$[*].name").isEqualTo(List.of("Склад на Подолі (новий)", "Пункт видачі у Львові"));
        assertThat(list).bodyJson().extractingPath("$[0].city").isEqualTo("Київ");

        assertThat(mvc.post().uri("/locations/import").session(olena).contentType(MediaType.APPLICATION_OCTET_STREAM)
                .content("не excel".getBytes(StandardCharsets.UTF_8))).hasStatus(400);
        assertThat(mvc.get().uri("/locations").session(login("keeper"))).hasStatus(403);
    }

    private MvcTestResult post(MockHttpSession session, String json) {
        return mvc.post().uri("/locations").session(session).contentType(MediaType.APPLICATION_JSON).content(json).exchange();
    }

    private static String body(MvcTestResult result) {
        try {
            return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }
}
