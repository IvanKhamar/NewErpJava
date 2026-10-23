package ua.newerp.application.production;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import ua.newerp.domain.production.ProductionComponent;
import ua.newerp.domain.production.ProductionModel;

/** Data for the sewing and cutting plans. */
public interface SewingPlanSource {

    /** Orders in these statuses with their lines and what each already holds by the sewing log. */
    List<PlanOrder> getPlanOrders(Collection<Integer> statusIds);

    /**
     * Production time by product id: production_period of the product's first category (the smallest id), as on the old site.
     * Products without a category are not in the map.
     */
    Map<Integer, Integer> getProductionDays(Collection<Integer> productIds);

    /** Models with sizes (read only, no lock). */
    List<ProductionModel> getPlanModels(Collection<Integer> productionIds);

    /** Active models of the "clothing" type with sizes (read only, no lock), for the cutting plan. */
    List<ProductionModel> getActiveClothingModels();

    /** Components with their cutting BOMs of these models (read only, no lock). */
    List<ProductionComponent> getComponents(Collection<Integer> productionIds);
}
