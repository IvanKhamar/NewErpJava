package ua.newerp.web.ui;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class StatusBadgeTest {

    @Test
    void textIsDarkOnLightColorsAndLightOnDarkOnes() {
        assertThat(new StatusBadge("Нове", "#ffcc00").getStyle().get("color")).isEqualTo("#000");
        assertThat(new StatusBadge("Скасовано", "#1a237e").getStyle().get("color")).isEqualTo("#fff");
    }

    @Test
    void withoutAColorTheBadgeKeepsTheThemeColors() {
        var badge = new StatusBadge("статус 9", "");
        assertThat(badge.getText()).isEqualTo("статус 9");
        assertThat(badge.getStyle().get("background-color")).isNull();
    }
}
