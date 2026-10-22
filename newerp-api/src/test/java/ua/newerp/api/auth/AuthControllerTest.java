package ua.newerp.api.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.jdbc.Sql;

import ua.newerp.IntegrationTest;

@Sql("/sql/users.sql")
class AuthControllerTest extends IntegrationTest {

    @Autowired
    JdbcClient jdbc;

    @Test
    void loginReturnsUserAndMeWorks() {
        assertThat(mvc.post().uri("/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\" olena \",\"password\":\"Passw0rd!\"}"))
                .hasStatusOk()
                .bodyJson().isEqualTo("""
                        {"userId":7,"username":"olena","fullName":"Олена Коваль","role":"Manager","mustChangePassword":false}
                        """);

        MockHttpSession session = login("olena");
        assertThat(mvc.get().uri("/auth/me").session(session)).bodyJson().extractingPath("$.role").isEqualTo("Manager");
        assertThat(jdbc.sql("SELECT last_login_at FROM erp_user_login WHERE user_id = 7").query(Object.class).single()).isNotNull();
    }

    @Test
    void wrongPasswordUnknownUserAndDisabledUserGetSame401() {
        for (String body : new String[] {
                "{\"username\":\"olena\",\"password\":\"nope\"}",
                "{\"username\":\"ghost\",\"password\":\"Passw0rd!\"}",
                "{\"username\":\"old\",\"password\":\"Passw0rd!\"}",   // disabled on the old site
                "{\"username\":\"andrii\",\"password\":\"Passw0rd!\"}" // no NewErp login
        }) {
            assertThat(mvc.post().uri("/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                    .hasStatus(401)
                    .bodyJson().extractingPath("$.title").isEqualTo("Невірний логін або пароль");
        }
    }

    @Test
    void logoutEndsSession() {
        MockHttpSession session = login("olena");
        assertThat(mvc.post().uri("/auth/logout").session(session)).hasStatus(204);
        assertThat(mvc.get().uri("/auth/me").session(session)).hasStatus(401);
    }

    @Test
    void changedStampOrDisabledUserEndsSessionAtOnce() {
        MockHttpSession session = login("olena");
        jdbc.sql("UPDATE erp_user_login SET security_stamp = 'other' WHERE user_id = 7").update();
        assertThat(mvc.get().uri("/auth/me").session(session)).hasStatus(401);

        MockHttpSession again = login("olena");
        jdbc.sql("UPDATE dd_user SET status = 0 WHERE user_id = 7").update();
        assertThat(mvc.get().uri("/auth/me").session(again)).hasStatus(401);
    }

    @Test
    void changePasswordKeepsThisSessionAndEndsOthers() {
        MockHttpSession first = login("olena");
        MockHttpSession second = login("olena");

        assertThat(mvc.post().uri("/auth/change-password").session(first).contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"Passw0rd!\",\"newPassword\":\"NewPassw0rd\"}"))
                .hasStatus(204);

        assertThat(mvc.get().uri("/auth/me").session(first)).hasStatusOk();
        assertThat(mvc.get().uri("/auth/me").session(second)).hasStatus(401);
        login("olena", "NewPassw0rd");
    }

    @Test
    void changePasswordRules() {
        MockHttpSession session = login("olena");
        assertThat(mvc.post().uri("/auth/change-password").session(session).contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"wrong\",\"newPassword\":\"NewPassw0rd\"}"))
                .hasStatus(400)
                .bodyJson().extractingPath("$.detail").isEqualTo("Поточний пароль неправильний.");
        assertThat(mvc.post().uri("/auth/change-password").session(session).contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"Passw0rd!\",\"newPassword\":\"short\"}"))
                .hasStatus(400)
                .bodyJson().extractingPath("$.detail").isEqualTo("Пароль має містити щонайменше 8 символів.");
    }
}
