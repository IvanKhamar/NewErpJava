package ua.newerp.web.ui;

import java.util.HexFormat;

import com.vaadin.flow.component.html.Span;

/**
 * An order status in the old site's colour. The text is black or white depending on how bright the background is,
 * so it reads on any colour (StatusChip.razor).
 */
public class StatusBadge extends Span {

    public StatusBadge(String name, String color) {
        super(name);
        addClassName("status-badge");
        if (color != null && color.matches("#[0-9a-fA-F]{6}")) {
            int rgb = HexFormat.fromHexDigits(color, 1, 7);
            double luminance = (0.299 * (rgb >> 16) + 0.587 * (rgb >> 8 & 0xFF) + 0.114 * (rgb & 0xFF)) / 255;
            getStyle().setBackgroundColor(color).setColor(luminance > 0.5 ? "#000" : "#fff");
        }
    }
}
