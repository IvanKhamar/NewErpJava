package ua.newerp.domain.production;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import ua.newerp.domain.production.ProductionComponent.BomRow;

class ProductionComponentTest {

    @Test
    void setBomValidatesSizesAndDuplicates() {
        ProductionComponent front = new ProductionComponent(7, " Перед ", 1);
        assertThat(front.getName()).isEqualTo("Перед");

        assertThatThrownBy(() -> front.setBom(List.of(new BomRow(9, 20, BigDecimal.ONE)), List.of(1, 2)))
                .hasMessage("Розмір 9 не належить моделі цієї деталі.");
        assertThatThrownBy(() -> front.setBom(List.of(new BomRow(null, 20, BigDecimal.ZERO)), List.of()))
                .hasMessage("Кількість розхідника на одну деталь має бути більшою за нуль.");
        assertThatThrownBy(() -> front.setBom(List.of(new BomRow(null, 20, BigDecimal.ONE), new BomRow(null, 20, BigDecimal.TEN)), List.of()))
                .hasMessage("Специфікація крою містить повторювані рядки.");

        front.setBom(List.of(new BomRow(null, 20, BigDecimal.ONE), new BomRow(1, 20, BigDecimal.TEN)), List.of(1));
        assertThat(front.getBom()).hasSize(2);
    }

    @Test
    void componentStockTakesWhatThereIs() {
        ComponentStock stock = new ComponentStock(3, 1);
        stock.add(new BigDecimal("4"));
        assertThat(stock.takeUpTo(new BigDecimal("6"))).isEqualByComparingTo("4");
        assertThat(stock.getStock()).isEqualByComparingTo("0");
    }
}
