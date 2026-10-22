package ua.newerp.api.users;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import com.jayway.jsonpath.JsonPath;

import ua.newerp.IntegrationTest;

@Sql("/sql/users.sql")
class UserControllerTest extends IntegrationTest {

    MockHttpSession admin;

    @BeforeEach
    void logIn() {
        admin = login("admin");
    }

    @Test
    void onlyAdmin() {
        assertThat(mvc.get().uri("/users").session(login("olena"))).hasStatus(403);
    }

    @Test
    void listShowsAccessFirstThenByName() {
        assertThat(mvc.get().uri("/users").session(admin))
                .hasStatusOk()
                .bodyJson().extractingPath("$[*].username").asArray()
                .containsExactly("admin", "keeper", "olena", "old", "andrii");
    }

    @Test
    void createUserWithTemporaryPasswordThenMustChangeIt() throws Exception {
        MvcTestResult created = mvc.post().uri("/users").session(admin).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"username":"petro","firstName":"Петро","lastName":"Шевченко","email":"p@example.com","role":"storekeeper"}
                        """)
                .exchange();
        assertThat(created).hasStatus(201);
        String body = created.getResponse().getContentAsString(StandardCharsets.UTF_8);
        int userId = JsonPath.read(body, "$.userId");
        String password = JsonPath.read(body, "$.temporaryPassword");
        assertThat(created.getResponse().getHeader("Location")).isEqualTo("/users/" + userId);
        assertThat(password).hasSize(12);

        MockHttpSession petro = login("petro", password);
        assertThat(mvc.get().uri("/auth/me").session(petro)).bodyJson().isLenientlyEqualTo("""
                {"username":"petro","fullName":"Петро Шевченко","role":"Storekeeper","mustChangePassword":true}
                """);

        assertThat(mvc.post().uri("/users").session(admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"petro\",\"firstName\":\"П\",\"lastName\":\"Ш\",\"role\":\"Manager\"}"))
                .hasStatus(400).bodyJson().extractingPath("$.detail").isEqualTo("Логін \"petro\" вже зайнятий.");
    }

    @Test
    void setAccessChangesRoleAndEndsTheirSessions() {
        MockHttpSession olena = login("olena");

        assertThat(mvc.put().uri("/users/7/access").session(admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\":\"Storekeeper\"}"))
                .hasStatusOk().bodyJson().isEqualTo("{\"userId\":7,\"temporaryPassword\":null}");

        assertThat(mvc.get().uri("/auth/me").session(olena)).hasStatus(401);
        assertThat(mvc.get().uri("/auth/me").session(login("olena"))).bodyJson().extractingPath("$.role").isEqualTo("Storekeeper");
    }

    @Test
    void newAccessGetsPassword() {
        assertThat(mvc.put().uri("/users/8/access").session(admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\":\"Manager\",\"temporaryPassword\":\"Tempor4ry\"}"))
                .hasStatusOk().bodyJson().isEqualTo("{\"userId\":8,\"temporaryPassword\":\"Tempor4ry\"}");
        login("andrii", "Tempor4ry");
    }

    @Test
    void adminCannotDemoteOrRevokeSelf() {
        assertThat(mvc.put().uri("/users/1/access").session(admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\":\"Manager\"}"))
                .hasStatus(400);
        assertThat(mvc.delete().uri("/users/1/access").session(admin)).hasStatus(400);
    }

    @Test
    void revokeAndReset() {
        assertThat(mvc.delete().uri("/users/7/access").session(admin)).hasStatus(204);
        assertThat(mvc.delete().uri("/users/7/access").session(admin)).hasStatus(404);

        assertThat(mvc.post().uri("/users/2/reset-password").session(admin).contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .hasStatusOk().bodyJson().extractingPath("$.temporaryPassword").isNotNull();
        assertThat(mvc.post().uri("/users/8/reset-password").session(admin).contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .hasStatus(404);
    }
}
