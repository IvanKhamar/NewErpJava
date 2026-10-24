package ua.newerp.domain.competitors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;

import org.junit.jupiter.api.Test;

import ua.newerp.domain.common.DomainException;

class CompetitorSiteTest {

    @Test
    void acceptsOnlyWebAddresses() {
        assertThat(CompetitorSite.requireUrl(" https://dress.example/p/1 ", "Сайт", 255)).hasToString("https://dress.example/p/1");
        assertThatThrownBy(() -> CompetitorSite.requireUrl("ftp://dress.example", "Сайт", 255)).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> CompetitorSite.requireUrl("dress.example", "Сайт", 255)).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> CompetitorSite.requireUrl("https://a b", "Сайт", 255)).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> CompetitorSite.requireUrl("", "Сайт", 255)).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> CompetitorSite.requireUrl("https://dress.example/" + "a".repeat(300), "Сайт", 255))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void aPageMustBeOnTheSiteOrItsSubdomain() {
        String site = CompetitorSite.host(URI.create("https://www.Dress.example/"));
        assertThat(site).isEqualTo("dress.example");
        assertThat(CompetitorSite.isOnSite(URI.create("https://dress.example/p/1"), site)).isTrue();
        assertThat(CompetitorSite.isOnSite(URI.create("https://m.dress.example/p/1"), site)).isTrue();
        assertThat(CompetitorSite.isOnSite(URI.create("https://notdress.example/p/1"), site)).isFalse();
        assertThat(CompetitorSite.isOnSite(URI.create("https://dress.example.evil.test/"), site)).isFalse();
    }
}
