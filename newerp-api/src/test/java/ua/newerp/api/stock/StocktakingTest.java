package ua.newerp.api.stock;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import ua.newerp.IntegrationTest;

@Sql({"/sql/users.sql", "/sql/stock.sql"})
class StocktakingTest extends IntegrationTest {

    @Test
    void theFilledSheetIsPreviewedAndAppliedWithLogEntries() throws IOException {
        MockHttpSession keeper = login("keeper");

        // The sheet of the fabrics: linen 3 m and cotton 50 m in the books. Linen is counted 2.5, cotton matches,
        // and a row of the inactive item 5 is added by hand.
        MvcTestResult sheet = mvc.get().uri("/stock/stocktaking/sheet.xlsx?categoryId=1").session(keeper).exchange();
        assertThat(sheet).hasStatusOk();
        byte[] filled;
        try (var book = new XSSFWorkbook(new ByteArrayInputStream(sheet.getResponse().getContentAsByteArray()))) {
            Sheet first = book.getSheetAt(0);
            assertThat(first.getRow(0).getCell(5).getStringCellValue()).isEqualTo("Фактично");
            assertThat(first.getLastRowNum()).isEqualTo(2);
            for (int r = 1; r <= 2; r++) {
                Row row = first.getRow(r);
                row.createCell(5).setCellValue(row.getCell(0).getNumericCellValue() == 1 ? "2,5" : "50");
            }
            Row extra = first.createRow(3);
            extra.createCell(0).setCellValue(5);
            extra.createCell(5).setCellValue(1);
            var output = new ByteArrayOutputStream();
            book.write(output);
            filled = output.toByteArray();
        }

        assertThat(mvc.post().uri("/stock/stocktaking/preview").session(keeper).contentType(MediaType.APPLICATION_OCTET_STREAM)
                .content(filled)).hasStatusOk().bodyJson().isLenientlyEqualTo("""
                {"lines":[{"itemId":2,"booked":50.000,"actual":50,"difference":0},
                          {"itemId":1,"name":"Льон білий","unit":"м","booked":3.000,"actual":2.5,"difference":-0.500}],
                 "errors":["Позиції 5 немає на складі."]}
                """);

        // Applying: linen −0.5 × 200 and threads +7 × 30 (counted by hand, not in the sheet); cotton matches
        assertThat(apply(keeper, "{\"counts\":[{\"itemId\":1,\"actual\":2.5},{\"itemId\":2,\"actual\":50},{\"itemId\":4,\"actual\":7}],"
                + "\"comment\":\"жовтень\"}")).hasStatusOk().bodyJson().isStrictlyEqualTo("{\"counted\":3,\"changed\":2,\"value\":110.00}");
        assertThat(mvc.get().uri("/stock/items/1/movements").session(keeper)).bodyJson()
                .isLenientlyEqualTo("[{\"delta\":-0.500,\"quantityAfter\":2.500,\"reason\":\"Інвентаризація: жовтень\"}]");

        // One wrong count and nothing is written
        assertThat(apply(keeper, "{\"counts\":[{\"itemId\":4,\"actual\":1},{\"itemId\":5,\"actual\":1}]}")).hasStatus(400);
        assertThat(mvc.get().uri("/stock/items/4").session(keeper)).bodyJson().isLenientlyEqualTo("{\"quantity\":7.000}");
        assertThat(apply(keeper, "{\"counts\":[{\"itemId\":4,\"actual\":-1}]}")).hasStatus(400);

        assertThat(mvc.get().uri("/stock/stocktaking/sheet.xlsx").session(login("olena"))).hasStatus(403);
    }

    private MvcTestResult apply(MockHttpSession session, String json) {
        return mvc.post().uri("/stock/stocktaking").session(session).contentType(MediaType.APPLICATION_JSON).content(json).exchange();
    }
}
