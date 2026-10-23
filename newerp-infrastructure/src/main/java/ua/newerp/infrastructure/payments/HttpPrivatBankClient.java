package ua.newerp.infrastructure.payments;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.web.client.RestClient;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import ua.newerp.application.payments.BankStatementClient;
import ua.newerp.application.payments.BankTransaction;
import ua.newerp.domain.common.DomainException;

/**
 * Statement through PrivatBank's Autoclient (api/proxy/transactions), like the old site (checkBusinessCards).
 * The answer format is reconstructed from the old site's code; not run against a real key yet.
 */
class HttpPrivatBankClient implements BankStatementClient {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final DateTimeFormatter REQUEST_DATE = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            DateTimeFormatter.ofPattern("dd.MM.yyyy"), DateTimeFormatter.ofPattern("yyyy-MM-dd"), DateTimeFormatter.ofPattern("dd-MM-yyyy"));
    private static final List<DateTimeFormatter> DATE_TIME_FORMATS = List.of(
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss"), DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"));
    private static final List<DateTimeFormatter> TIME_FORMATS = List.of(
            DateTimeFormatter.ofPattern("HH:mm"), DateTimeFormatter.ofPattern("HH:mm:ss"), DateTimeFormatter.ofPattern("H:mm"));

    private final RestClient http;
    private final PrivatBankProperties options;

    HttpPrivatBankClient(RestClient http, PrivatBankProperties options) {
        this.http = http;
        this.options = options;
    }

    @Override
    public boolean isConfigured() {
        return options.isConfigured();
    }

    @Override
    public List<BankTransaction> fetch(LocalDate from, LocalDate to) {
        var result = new ArrayList<BankTransaction>();
        for (PrivatBankProperties.Account account : options.accounts()) {
            if (!account.isSet()) {
                continue;
            }
            String url = options.baseUrl().replaceAll("/+$", "") + "/api/proxy/transactions?startDate=" + from.format(REQUEST_DATE)
                    + "&endDate=" + to.format(REQUEST_DATE);
            Response response = http.get().uri(url).header("id", account.id()).header("token", account.token())
                    .exchange((req, res) -> new Response(res.getStatusCode().value(), decode(res.getBody().readAllBytes())));
            if (response.status() < 200 || response.status() >= 300) {
                throw new DomainException("ПриватБанк відмовив (" + response.status() + "): " + shorten(response.body()));
            }
            result.addAll(parse(response.body()));
        }
        return result;
    }

    /**
     * {"StatementsResponse": {"statements": [{"account": [{"anything": {transaction fields}}]}]}}.
     * Fields: ID, TRANTYPE (C: incoming, D: outgoing), BPL_SUM_E, BPL_DAT_KL, BPL_TIM_P, BPL_OSND (purpose),
     * BPL_A_NAM, BPL_A_ACC, BPL_A_MFO, BPL_A_CRF (payer), BPL_C_BAL (balance).
     */
    static List<BankTransaction> parse(String json) {
        JsonNode statements;
        try {
            statements = JSON.readTree(json).path("StatementsResponse").path("statements");
        } catch (JacksonException e) {
            throw new DomainException("ПриватБанк повернув невідому відповідь: " + shorten(json));
        }
        if (!statements.isArray()) {
            throw new DomainException("ПриватБанк повернув невідому відповідь: " + shorten(json));
        }

        var result = new ArrayList<BankTransaction>();
        for (JsonNode statement : statements) {
            if (!statement.isObject()) {
                continue;
            }
            for (Map.Entry<String, JsonNode> entry : statement.properties()) {
                String account = entry.getKey();
                if (!entry.getValue().isArray()) {
                    continue;
                }
                for (JsonNode wrapper : entry.getValue()) {
                    // Each transaction is wrapped in an object with one field (the old site took current()).
                    if (!wrapper.isObject() || wrapper.isEmpty()) {
                        continue;
                    }
                    JsonNode p = wrapper.properties().iterator().next().getValue();
                    if (!p.isObject()) {
                        continue;
                    }
                    BigDecimal amount = money(text(p, "BPL_SUM_E"));
                    result.add(new BankTransaction(text(p, "ID"), account, parseDate(text(p, "BPL_DAT_KL")), parseTime(text(p, "BPL_TIM_P")),
                            amount == null ? BigDecimal.ZERO : amount, money(text(p, "BPL_C_BAL")), !"D".equals(text(p, "TRANTYPE")),
                            text(p, "BPL_OSND"), text(p, "BPL_A_NAM"), text(p, "BPL_A_ACC"), text(p, "BPL_A_MFO"), text(p, "BPL_A_CRF")));
                }
            }
        }
        return result;
    }

    /** The old site converted the answer from windows-1251. If it is valid UTF-8 already, it stays as is. */
    static String decode(byte[] bytes) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException e) {
            return new String(bytes, Charset.forName("windows-1251"));
        }
    }

    private static LocalDate parseDate(String text) {
        for (DateTimeFormatter format : DATE_FORMATS) {
            try {
                return LocalDate.parse(text, format);
            } catch (DateTimeParseException e) {
                // try the next format
            }
        }
        for (DateTimeFormatter format : DATE_TIME_FORMATS) {
            try {
                return LocalDateTime.parse(text, format).toLocalDate();
            } catch (DateTimeParseException e) {
                // try the next format
            }
        }
        throw new DomainException("ПриватБанк повернув незрозумілу дату: «" + text + "».");
    }

    private static LocalTime parseTime(String text) {
        for (DateTimeFormatter format : TIME_FORMATS) {
            try {
                return LocalTime.parse(text, format);
            } catch (DateTimeParseException e) {
                // try the next format
            }
        }
        return LocalTime.MIDNIGHT;
    }

    private static BigDecimal money(String text) {
        try {
            return new BigDecimal(text.replace(',', '.').replace(" ", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String text(JsonNode node, String name) {
        JsonNode value = node.path(name);
        if (value.isMissingNode() || value.isNull() || value.isContainer()) {
            return "";
        }
        return (value.isString() ? value.stringValue() : value.toString()).strip();
    }

    private static String shorten(String text) {
        return text.length() > 300 ? text.substring(0, 300) + "…" : text;
    }

    private record Response(int status, String body) {
    }
}
