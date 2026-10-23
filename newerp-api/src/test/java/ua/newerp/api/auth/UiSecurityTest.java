package ua.newerp.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import ua.newerp.IntegrationTest;

/** Security of the Vaadin UI under /ui/: the login page, the form login and one session shared with the API. */
@Sql("/sql/users.sql")
class UiSecurityTest extends IntegrationTest {

    @Test
    void anonymousUserIsSentToTheLoginPage() {
        assertThat(mvc.get().uri("/ui/orders")).hasStatus3xxRedirection().redirectedUrl().endsWith("/ui/login");
        assertThat(mvc.get().uri("/")).hasStatus3xxRedirection().hasRedirectedUrl("/ui/");
    }

    @Test
    void formLoginOpensASessionTheApiAccepts() {
        MvcTestResult result = mvc.post().uri("/ui/login").with(csrf())
                .param("username", "olena").param("password", PASSWORD).exchange();
        assertThat(result).hasStatus3xxRedirection();
        var session = (MockHttpSession) result.getMvcResult().getRequest().getSession(false);

        assertThat(mvc.get().uri("/auth/me").session(session)).hasStatusOk()
                .bodyJson().extractingPath("$.username").isEqualTo("olena");
    }

    @Test
    void wrongPasswordGoesBackToTheLoginPageWithAnError() {
        assertThat(mvc.post().uri("/ui/login").with(csrf()).param("username", "olena").param("password", "wrong"))
                .hasStatus3xxRedirection().redirectedUrl().endsWith("/ui/login?error");
    }
}
